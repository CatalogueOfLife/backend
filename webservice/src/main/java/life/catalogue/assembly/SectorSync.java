package life.catalogue.assembly;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.EstimateDao;
import life.catalogue.dao.FileMetricsSectorDao;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.match.EstimateRematcher;
import life.catalogue.match.MatchingDao;
import life.catalogue.match.RematchRequest;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.NameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static life.catalogue.dao.DatasetImportDao.countMap;

/**
 * Syncs/imports source data for a given sector into the assembled catalogue
 */
public class SectorSync extends SectorRunnable {
  private static final Logger LOG = LoggerFactory.getLogger(SectorSync.class);
  private final FileMetricsSectorDao fmDao;

  public SectorSync(DSID<Integer> sectorKey, SqlSessionFactory factory, NameUsageIndexService indexService, FileMetricsSectorDao fmDao,
                    Consumer<SectorRunnable> successCallback,
                    BiConsumer<SectorRunnable, Exception> errorCallback, User user) throws IllegalArgumentException {
    super(sectorKey, true, factory, indexService, successCallback, errorCallback, user);
    this.fmDao = fmDao;
  }
  
  @Override
  void doWork() throws Exception {
    sync();
    metrics();
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

  private void metrics() {
    try (SqlSession session = factory.openSession(true)) {
      // generate import metrics
      SectorImportMapper mapper = session.getMapper(SectorImportMapper.class);
      final int key = sector.getId();
      state.setDistributionCount(mapper.countDistribution(sectorKey.getDatasetKey(), key));
      state.setMediaCount(mapper.countMedia(sectorKey.getDatasetKey(), key));
      state.setNameCount(mapper.countName(sectorKey.getDatasetKey(), key));
      state.setReferenceCount(mapper.countReference(sectorKey.getDatasetKey(), key));
      state.setTaxonCount(mapper.countTaxon(sectorKey.getDatasetKey(), key));
      state.setSynonymCount(mapper.countSynonym(sectorKey.getDatasetKey(), key));
      state.setVernacularCount(mapper.countVernacular(sectorKey.getDatasetKey(), key));
      state.setTreatmentCount(mapper.countTreatment(sectorKey.getDatasetKey(), key));
      state.setIssuesCount(countMap(Issue.class, mapper.countIssues(sectorKey.getDatasetKey(), key)));
  
      state.setDistributionsByGazetteerCount(countMap(Gazetteer.class, mapper.countDistributionsByGazetteer(sectorKey.getDatasetKey(), key)));
      state.setIssuesCount(countMap(Issue.class, mapper.countIssues(sectorKey.getDatasetKey(), key)));
      state.setMediaByTypeCount(countMap(MediaType.class, mapper.countMediaByType(sectorKey.getDatasetKey(), key)));
      state.setNameRelationsByTypeCount(countMap(NomRelType.class, mapper.countNameRelationsByType(sectorKey.getDatasetKey(), key)));
      state.setNamesByOriginCount(countMap(Origin.class, mapper.countNamesByOrigin(sectorKey.getDatasetKey(), key)));
      state.setNamesByRankCount(countMap(DatasetImportDao::parseRank, mapper.countNamesByRank(sectorKey.getDatasetKey(), key)));
      state.setNamesByStatusCount(countMap(NomStatus.class, mapper.countNamesByStatus(sectorKey.getDatasetKey(), key)));
      state.setNamesByTypeCount(countMap(NameType.class, mapper.countNamesByType(sectorKey.getDatasetKey(), key)));
      state.setTaxaByRankCount(countMap(DatasetImportDao::parseRank, mapper.countTaxaByRank(sectorKey.getDatasetKey(), key)));
      state.setTaxonRelationsByTypeCount(countMap(TaxRelType.class, mapper.countTaxonRelationsByType(sectorKey.getDatasetKey(), key)));
      state.setUsagesByStatusCount(countMap(TaxonomicStatus.class, mapper.countUsagesByStatus(sectorKey.getDatasetKey(), key)));
      state.setVernacularsByLanguageCount(countMap(mapper.countVernacularsByLanguage(sectorKey.getDatasetKey(), key)));

      try {
        fmDao.updateTree(sector, state.getAttempt());
        fmDao.updateNames(sector, state.getAttempt());
      } catch (IOException e) {
        LOG.error("Failed to print sector {} of catalogue {}", sector.getKey(), sectorKey.getDatasetKey(), e);
      }
    }
  }
  
  private void sync() throws InterruptedException {
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
      state.setState( ImportState.INDEXING);
      indexService.indexSector(sector);
    }
  
    state.setState( ImportState.FINISHED);
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
    try (SqlSession session = factory.openSession(false);
         TreeCopyHandler treeHandler = new TreeCopyHandler(decisions, factory, user, sector, state)
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
          if (child.isSynonym()) {
            if (!blockedIds.contains(child.getId())) {
              LOG.info("Add synonym child {}", child);
              treeHandler.accept(child);
            }
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
    }
  }
  
  private void deleteOld() {
    int count;
    try (SqlSession session = factory.openSession(true)) {
      NameUsageMapper um = session.getMapper(NameUsageMapper.class);
      count = um.deleteBySector(sector);
      LOG.info("Deleted {} existing taxa with their synonyms and related information from sector {}", count, sector);
  
      NameMapper nm = session.getMapper(NameMapper.class);
      count = nm.deleteBySector(sector);
      LOG.info("Deleted {} names from sector {}", count, sector);
  
      ReferenceMapper rm = session.getMapper(ReferenceMapper.class);
      count = rm.deleteBySector(sector);
      LOG.info("Deleted {} references from sector {}", count, sector);
    }
  }
  
}
