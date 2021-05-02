package life.catalogue.assembly;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.dao.EstimateDao;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.db.SectorProcessable;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.NameIndex;
import life.catalogue.matching.decision.EstimateRematcher;
import life.catalogue.matching.decision.MatchingDao;
import life.catalogue.matching.decision.RematchRequest;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Syncs/imports source data for a given sector into the assembled catalogue
 */
public class SectorSync extends SectorRunnable {
  private static final Logger LOG = LoggerFactory.getLogger(SectorSync.class);

  private static final List<Class<? extends SectorProcessable<?>>> SECTOR_MAPPERS = List.of(
    VernacularNameMapper.class,
    DistributionMapper.class,
    MediaMapper.class,
    NameUsageMapper.class,
    TypeMaterialMapper.class,
    NameRelationMapper.class,
    NameMatchMapper.class,
    NameMapper.class,
    ReferenceMapper.class
  );

  private final SectorImportDao sid;
  private final NameIndex nameIndex;

  public SectorSync(DSID<Integer> sectorKey, SqlSessionFactory factory, NameIndex nameIndex, NameUsageIndexService indexService, SectorImportDao sid,
                    Consumer<SectorRunnable> successCallback,
                    BiConsumer<SectorRunnable, Exception> errorCallback, User user) throws IllegalArgumentException {
    super(sectorKey, true, true, factory, indexService, sid, successCallback, errorCallback, user);
    this.sid = sid;
    this.nameIndex = nameIndex;
  }
  
  @Override
  void doWork() throws Exception {
    state.setState( ImportState.DELETING);
    relinkForeignChildren();
    try {
      deleteOld();
      checkIfCancelled();

      state.setState(ImportState.INSERTING);
      processTree();
      checkIfCancelled();

    } finally {
      // run these even if we get errors in the main tree copying
      state.setState( ImportState.MATCHING);
      rematchForeignChildren();
      relinkAttachedSectors();
      rematchEstimates();
    }
  }

  @Override
  void doMetrics() throws Exception {
    // build metrics
    sid.updateMetrics(state, sectorKey.getDatasetKey());
  }

  @Override
  void updateSearchIndex() throws Exception {
    indexService.indexSector(sector);
    LOG.info("Reindexed sector {} from search index", sectorKey);
  }

  @Override
  void init() throws Exception {
    super.init();
    // also load all sector subject to auto block them
    try (SqlSession session = factory.openSession()) {
      AtomicInteger counter = new AtomicInteger();
      session.getMapper(SectorMapper.class).processSectors(sectorKey.getDatasetKey(), subjectDatasetKey).forEach(s -> {
        if (!s.getId().equals(sectorKey.getId()) && s.getSubject().getId() != null) {
          EditorialDecision d = new EditorialDecision();
          d.setSubject(s.getSubject());
          d.setDatasetKey(sectorKey.getDatasetKey());
          d.setSubjectDatasetKey(subjectDatasetKey);
          d.setMode(EditorialDecision.Mode.BLOCK);
          d.setNote("Auto blocked subject of sector " + s.getId());
          decisions.put(s.getSubject().getId(), d);
          counter.incrementAndGet();
        }
      });
      LOG.info("Loaded {} sector subjects for auto blocking", counter);
    }
  }

  /**
   * Rematch all broken estimates that fall into this sector
   */
  private void rematchEstimates() {
    RematchRequest req = new RematchRequest(sectorKey.getDatasetKey(), true);
    EstimateRematcher.match(new EstimateDao(factory), req, user.getKey());
  }

  /**
   * Temporarily relink all foreign children to the target taxon
   * so we don't break referential integrity when deleting the sector.
   */
  private void relinkForeignChildren() {
    final String newParentID = sector.getTarget().getId();
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

  private void processTree() {
    final Set<String> blockedIds = decisions.values().stream()
        .filter(ed -> ed.getMode().equals(EditorialDecision.Mode.BLOCK) && ed.getSubject().getId() != null)
        .map(ed -> ed.getSubject().getId())
        .collect(Collectors.toSet());

    Map<String, TreeCopyHandler.Usage> usageIds;
    Map<String, String> nameIds;
    try (SqlSession session = factory.openSession(false);
         TreeCopyHandler treeHandler = new TreeCopyHandler(decisions, factory, nameIndex, user, sector, state)
    ){
      NameUsageMapper um = session.getMapper(NameUsageMapper.class);
      LOG.info("{} taxon tree {} to {}. Blocking {} nodes", sector.getMode(), sector.getSubject(), sector.getTarget(), blockedIds.size());
      if (sector.getMode() == Sector.Mode.ATTACH) {
        um.processTree(subjectDatasetKey, null, sector.getSubject().getId(), blockedIds, null, true,false)
            .forEach(treeHandler);

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
            um.processTree(subjectDatasetKey, null, child.getId(), blockedIds, null, true,false)
                .forEach(treeHandler);
          }
          treeHandler.reset();
        }

      } else {
        throw new NotImplementedException("Only attach and union sectors are implemented");
      }

      LOG.info("Sync name & taxon relations from sector {}", sectorKey);
      treeHandler.copyRelations();

      // copy handler stats to metrics
      state.setAppliedDecisionCount(treeHandler.decisionCounter);
      state.setIgnoredByReasonCount(Map.copyOf(treeHandler.ignoredCounter));
    }
  }

  private void deleteOld() {
    try (SqlSession session = factory.openSession(true)) {
      for (Class<? extends SectorProcessable<?>> m : SECTOR_MAPPERS) {
        int count = session.getMapper(m).deleteBySector(sector);
        LOG.info("Deleted {} existing {}s from sector {}", count, m.getSimpleName().replaceAll("Mapper", ""), sector);
      }
    }
  }
  
}
