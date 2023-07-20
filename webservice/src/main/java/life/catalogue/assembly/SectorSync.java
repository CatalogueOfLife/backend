package life.catalogue.assembly;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.common.lang.InterruptedRuntimeException;
import life.catalogue.dao.EstimateDao;
import life.catalogue.dao.SectorDao;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.db.PgUtils;
import life.catalogue.db.SectorProcessable;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.NameIndex;
import life.catalogue.matching.UsageMatcherGlobal;
import life.catalogue.matching.decision.EstimateRematcher;
import life.catalogue.matching.decision.MatchingDao;
import life.catalogue.matching.decision.RematchRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Syncs/imports source data for a given sector into the assembled catalogue
 */
public class SectorSync extends SectorRunnable {
  private static final Logger LOG = LoggerFactory.getLogger(SectorSync.class);

  private final EstimateDao estimateDao;
  private final SectorImportDao sid;
  private final NameIndex nameIndex;
  private final UsageMatcherGlobal matcher;
  private final boolean project;
  private boolean disableAutoBlocking;
  private final int targetDatasetKey; // dataset to sync into
  private final @Nullable Taxon incertae;
  private List<SimpleName> foreignChildren;

  SectorSync(DSID<Integer> sectorKey, int targetDatasetKey, boolean project, @Nullable Taxon incertae,
             SqlSessionFactory factory, NameIndex nameIndex, UsageMatcherGlobal matcher, EventBus bus,
             NameUsageIndexService indexService, SectorDao sdao, SectorImportDao sid, EstimateDao estimateDao,
             Consumer<SectorRunnable> successCallback, BiConsumer<SectorRunnable, Exception> errorCallback, User user) throws IllegalArgumentException {
    super(sectorKey, true, true, project, factory, matcher, indexService, sdao, sid, bus, successCallback, errorCallback, user);
    this.project = project;
    this.sid = sid;
    this.estimateDao = estimateDao;
    this.nameIndex = nameIndex;
    this.matcher = matcher;
    this.targetDatasetKey = targetDatasetKey;
    if (targetDatasetKey != sectorKey.getDatasetKey()) {
      LOG.info("Syncing sector {} into release {}", sectorKey, targetDatasetKey);
    }
    this.incertae = incertae;
  }
  
  @Override
  void doWork() throws Exception {
    if (project) {
      state.setState( ImportState.DELETING);
      relinkForeignChildren();
    }
    try {
      if (project) {
        deleteOld();
        checkIfCancelled();
      }

      state.setState(ImportState.INSERTING);
      processTree();
      checkIfCancelled();

    } finally {
      if (project) {
        // run these even if we get errors in the main tree copying
        state.setState( ImportState.MATCHING);
        rematchForeignChildren();
        relinkAttachedSectors();
        rematchEstimates();
      }
    }
  }

  @Override
  void doMetrics() throws Exception {
    // build metrics
    sid.updateMetrics(state, sectorKey.getDatasetKey());
  }

  @Override
  void updateSearchIndex() throws Exception {
    if (project) {
      indexService.indexSector(sector);
      LOG.info("Reindexed sector {} from search index", sectorKey);

    } else {
      LOG.debug("Will index merge sector {} at the end of the release. Skip immediate indexing", sectorKey);
    }
  }

  @Override
  protected Sector loadSectorAndUpdateDatasetImport(boolean validate) {
    Sector s = super.loadSectorAndUpdateDatasetImport(validate);
    if (s.getTargetID() == null && incertae != null) {
      LOG.debug("Use incertae sedis target {}", incertae);
      s.setTarget(SimpleNameLink.of(incertae));
    }
    return s;
  }

  @VisibleForTesting
  void setDisableAutoBlocking(boolean disableAutoBlocking) {
    this.disableAutoBlocking = disableAutoBlocking;
  }

  @Override
  void init() throws Exception {
    super.init(true);
    loadForeignChildren();
    if (!disableAutoBlocking) {
      // also load all sector subjects to auto block them
      try (SqlSession session = factory.openSession()) {
        AtomicInteger counter = new AtomicInteger();
        PgUtils.consume(
          () -> session.getMapper(SectorMapper.class).processSectors(sectorKey.getDatasetKey(), subjectDatasetKey),
          s -> {
            if (!s.getId().equals(sectorKey.getId()) && s.getSubject() != null && s.getSubject().getId() != null) {
              EditorialDecision d = new EditorialDecision();
              d.setSubject(s.getSubject());
              d.setDatasetKey(sectorKey.getDatasetKey());
              d.setSubjectDatasetKey(subjectDatasetKey);
              d.setMode(EditorialDecision.Mode.BLOCK);
              d.setNote("Auto blocked subject of sector " + s.getId());
              decisions.put(s.getSubject().getId(), d);
              counter.incrementAndGet();
            }
          }
        );
        LOG.info("Loaded {} sector subjects for auto blocking", counter);
      }
    }
  }

  private void loadForeignChildren() {
    try (SqlSession session = factory.openSession(true)) {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      foreignChildren = num.foreignChildren(sectorKey);
    }
    LOG.info("Loaded {} children from other sectors with a parent from sector {}", foreignChildren.size(), sectorKey);
  }

  /**
   * Rematch all broken estimates that fall into this sector
   */
  private void rematchEstimates() {
    RematchRequest req = new RematchRequest(sectorKey.getDatasetKey(), true);
    EstimateRematcher.match(estimateDao, req, user.getKey());
  }

  /**
   * Temporarily relink all foreign children to the target taxon
   * so we don't break referential integrity when deleting the sector.
   */
  private void relinkForeignChildren() {
    final String newParentID = sector.getTargetID(); // can be null !!!
    processForeignChildren((num, sn) -> {
        // remember original parent
        NameUsage parent = num.get(DSID.of(sectorKey.getDatasetKey(), sn.getParent()));
        foreignChildrenParents.put(sn.getId(), parent.getName());
        // update to new parent
        num.updateParentId(DSID.of(sectorKey.getDatasetKey(), sn.getId()), newParentID, user.getKey());
    });
  }
  
  /**
   * Link all foreign children back to their original parent inside the sector.
   * If parent does not exist anymore keep it linked to the sectors target taxon.
   */
  private void rematchForeignChildren() {
    try (SqlSession session = factory.openSession(false)) {
      final MatchingDao mdao = new MatchingDao(session);
      
      processForeignChildren((num, sn) -> {
        Name parent = foreignChildrenParents.get(sn.getId());
        List<Taxon> matches = mdao.matchSector(parent, sector);
        if (matches.isEmpty()) {
          LOG.warn("{} with parent {} in sector {} cannot be rematched", sn.getName(), parent, sector.getKey());
        } else {
          if (matches.size() > 1) {
            LOG.warn("{} with parent {} in sector {} matches {} times - pick first {}", sn.getName(), parent, sector.getKey(), matches.size(), matches.get(0));
          }
          num.updateParentId(DSID.of(sectorKey.getDatasetKey(), sn.getId()), matches.get(0).getId(), user.getKey());
        }
      });
    }
  }
  
  private void processForeignChildren(BiConsumer<NameUsageMapper, SimpleName> processor) {
    int counter = 0;
    try (SqlSession session = factory.openSession(ExecutorType.BATCH, false)) {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      for (SimpleName u : foreignChildren) {
        processor.accept(num, u);
        if (counter++ % 10000 == 0) {
          session.commit();
        }
      }
      session.commit();
    }
  }
  
  private void relinkAttachedSectors() {
    try (SqlSession session = factory.openSession(false)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      MatchingDao mdao = new MatchingDao(session);
      for (Sector s : childSectors) {
        List<Taxon> matches = mdao.matchSector(s.getTarget(), sector);
        if (matches.size()==1) {
          s.getTarget().setId(matches.get(0).getId());
          
        } else {
          String warning;
          s.getTarget().setId(null);
          if (matches.isEmpty()) {
            warning = "Child sector " + s.getKey() + " cannot be rematched to synced sector " + sector.getKey() + " - lost " + s.getTarget();
          } else {
            warning = "Child sector " + s.getKey() + " cannot be rematched to synced sector " + sector.getKey() + " - multiple names like  " + s.getTarget();
          }
          LOG.warn(warning);
          state.addWarning(warning);
        }
        sm.update(s);
      }
      session.commit();
    }
  }

  private TreeHandler sectorHandler(){
    if (sector.getMode() == Sector.Mode.MERGE) {
      return new TreeMergeHandler(targetDatasetKey, decisions, factory, nameIndex, matcher, user, sector, state, incertae);
    }
    return new TreeCopyHandler(targetDatasetKey, decisions, factory, nameIndex, user, sector, state);
  }

  /**
   * Make sure to apply all changes to targetDatasetKey not the sectors datasetKey!
   */
  private void processTree() throws InterruptedException {
    final Set<String> blockedIds = decisions.values().stream()
        .filter(ed -> ed.getMode().equals(EditorialDecision.Mode.BLOCK) && ed.getSubject().getId() != null)
        .map(ed -> ed.getSubject().getId())
        .collect(Collectors.toSet());

    try (SqlSession session = factory.openSession(false);
         TreeHandler treeHandler = sectorHandler()
    ){
      NameUsageMapper um = session.getMapper(NameUsageMapper.class);
      LOG.info("{} taxon tree {} to {}. Blocking {} nodes", sector.getMode(), sector.getSubject(), sector.getTarget(), blockedIds.size());

      if (sector.getMode() == Sector.Mode.ATTACH || sector.getMode() == Sector.Mode.MERGE) {
        String rootID = sector.getSubject() == null ? null : sector.getSubject().getId();
        TreeTraversalParameter ttp = TreeTraversalParameter.dataset(subjectDatasetKey, rootID, blockedIds);
        PgUtils.consume(
          () -> um.processTree(ttp, sector.getMode() == Sector.Mode.MERGE), treeHandler
        );

      } else if (sector.getMode() == Sector.Mode.UNION) {
        LOG.info("Traverse taxon tree at {}, ignoring immediate children above rank {}. Blocking {} nodes", sector.getSubject().getId(), sector.getPlaceholderRank(), blockedIds.size());
        // in UNION mode do not attach the subject itself, just its children
        // if we have a placeholder rank configured ignore children of that rank or higher
        // see https://github.com/CatalogueOfLife/clearinghouse-ui/issues/518
        for (NameUsageBase child : um.children(DSID.of(subjectDatasetKey, sector.getSubject().getId()), sector.getPlaceholderRank())){
          if (blockedIds.contains(child.getId())) {
            LOG.info("Skip blocked child {}", child);
            continue;
          }
          if (child.isSynonym()) {
            LOG.info("Add synonym child {}", child);
            treeHandler.accept(child);
          } else {
            LOG.info("Traverse child {}", child);
            TreeTraversalParameter ttp = TreeTraversalParameter.dataset(subjectDatasetKey, child.getId(), blockedIds);
            PgUtils.consume(
              () -> um.processTree(ttp, false),
              treeHandler
            );
          }
          treeHandler.reset();
        }

      } else {
        throw new NotImplementedException(sector.getMode() + " sectors are not supported");
      }

      LOG.info("Synced {} taxa and {} synonyms from sector {}", state.getTaxonCount(), state.getSynonymCount(), sectorKey);
      LOG.info("Sync name & taxon relations from sector {}", sectorKey);
      treeHandler.copyRelations();

      // copy handler stats to metrics
      state.setAppliedDecisionCount(treeHandler.getDecisionCounter());
      state.setIgnoredByReasonCount(Map.copyOf(treeHandler.getIgnoredCounter()));

    } catch (InterruptedRuntimeException e) {
      // tree handlers are throwing consumer which wrap exceptions as runtime exceptions - unpack them!
      throw e.asChecked();
    }
  }

  private void deleteOld() {
    if (!sector.getDatasetKey().equals(targetDatasetKey)) {
      throw new IllegalArgumentException(String.format("Deleting sector data can only be done in the project %s, not in dataset %s", sector.getDatasetKey(), targetDatasetKey));
    }
    try (SqlSession session = factory.openSession(true)) {
      // TODO: deal with species estimates separately as they are on a shared table
      for (Class<? extends SectorProcessable<?>> m : SectorProcessable.MAPPERS) {
        int count = session.getMapper(m).deleteBySector(sector);
        LOG.info("Deleted {} existing {}s from sector {}", count, m.getSimpleName().replaceAll("Mapper", ""), sector);
      }
    }
  }
  
}
