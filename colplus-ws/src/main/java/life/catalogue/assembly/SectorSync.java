package life.catalogue.assembly;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import life.catalogue.db.mapper.ReferenceMapper;
import life.catalogue.db.mapper.SectorImportMapper;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Gazetteer;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.MediaType;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.api.vocab.NomStatus;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.MatchingDao;
import life.catalogue.dao.NamesTreeDao;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.es.name.index.NameUsageIndexService;
import org.gbif.nameparser.api.NameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static life.catalogue.dao.DatasetImportDao.countMap;

/**
 * Syncs/imports source data for a given sector into the assembled catalogue
 */
public class SectorSync extends SectorRunnable {
  private static final Logger LOG = LoggerFactory.getLogger(SectorSync.class);
  private NamesTreeDao treeDao;
  
  public SectorSync(int sectorKey, SqlSessionFactory factory, NameUsageIndexService indexService, DatasetImportDao diDao,
                    Consumer<SectorRunnable> successCallback,
                    BiConsumer<SectorRunnable, Exception> errorCallback, ColUser user) throws IllegalArgumentException {
    super(sectorKey, true, factory, indexService, successCallback, errorCallback, user);
    treeDao = diDao.getTreeDao();
  }
  
  @Override
  void doWork() throws Exception {
    sync();
    metrics();
  }
  
  @Override
  void finalWork() {
    try (SqlSession session = factory.openSession(true)) {
      SectorImportMapper sim = session.getMapper(SectorImportMapper.class);
      sim.create(state);
    }
  }

  private void metrics() {
    try (SqlSession session = factory.openSession(true)) {
      SectorImportMapper mapper = session.getMapper(SectorImportMapper.class);
      final int key = sector.getKey();
      state.setDescriptionCount(mapper.countDescription(catalogueKey, key));
      state.setDistributionCount(mapper.countDistribution(catalogueKey, key));
      state.setMediaCount(mapper.countMedia(catalogueKey, key));
      state.setNameCount(mapper.countName(catalogueKey, key));
      state.setReferenceCount(mapper.countReference(catalogueKey, key));
      state.setTaxonCount(mapper.countTaxon(catalogueKey, key));
      state.setSynonymCount(mapper.countSynonym(catalogueKey, key));
      state.setVernacularCount(mapper.countVernacular(catalogueKey, key));
      state.setIssuesCount(countMap(Issue.class, mapper.countIssues(catalogueKey, key)));
  
      state.setDistributionsByGazetteerCount(countMap(Gazetteer.class, mapper.countDistributionsByGazetteer(catalogueKey, key)));
      state.setIssuesCount(countMap(Issue.class, mapper.countIssues(catalogueKey, key)));
      state.setMediaByTypeCount(countMap(MediaType.class, mapper.countMediaByType(catalogueKey, key)));
      state.setNameRelationsByTypeCount(countMap(NomRelType.class, mapper.countNameRelationsByType(catalogueKey, key)));
      state.setNamesByOriginCount(countMap(Origin.class, mapper.countNamesByOrigin(catalogueKey, key)));
      state.setNamesByRankCount(countMap(DatasetImportDao::parseRank, mapper.countNamesByRank(catalogueKey, key)));
      state.setNamesByStatusCount(countMap(NomStatus.class, mapper.countNamesByStatus(catalogueKey, key)));
      state.setNamesByTypeCount(countMap(NameType.class, mapper.countNamesByType(catalogueKey, key)));
      state.setTaxaByRankCount(countMap(DatasetImportDao::parseRank, mapper.countTaxaByRank(catalogueKey, key)));
      state.setUsagesByStatusCount(countMap(TaxonomicStatus.class, mapper.countUsagesByStatus(catalogueKey, key)));
      state.setVernacularsByLanguageCount(countMap(mapper.countVernacularsByLanguage(catalogueKey, key)));

      try {
        treeDao.updateSectorTree(sector.getKey(), state.getAttempt());
        treeDao.updateSectorNames(sector.getKey(), state.getAttempt());
      } catch (IOException e) {
        LOG.error("Failed to print sector {} of catalogue {}", sector.getKey(), catalogueKey, e);
      }
    }
  }
  
  private void sync() throws InterruptedException {

    state.setState( SectorImport.State.DELETING);
    relinkForeignChildren();
    try {
      deleteOld();
      checkIfCancelled();
  
      state.setState(SectorImport.State.COPYING);
      processTree();
      checkIfCancelled();
  
    } finally {
      // run these even if we get errors in the main tree copying
      state.setState( SectorImport.State.RELINKING);
      rematchForeignChildren();
      relinkAttachedSectors();
      state.setState( SectorImport.State.INDEXING);
      indexService.indexSector(sector);
    }
  
    state.setState( SectorImport.State.FINISHED);
  }
  
  /**
   * Temporarily relink all foreign children to the target taxon
   * so we don't break referential integrity when deleting the sector.
   */
  private void relinkForeignChildren() {
    final String newParentID = sector.getTarget().getId();
    processForeignChildren((tm, t) -> {
        // remember original parent
        Taxon parent = tm.get(DSID.key(catalogueKey, t.getParentId()));
        foreignChildrenParents.put(t.getId(), parent.getName());
        // update to new parent
        t.setParentId(newParentID);
        tm.update(t);
    });
  }
  
  /**
   * Link all foreign children back to their original parent inside the sector.
   * If parent does not exist anymore keep it linked to the sectors target taxon.
   */
  private void rematchForeignChildren() {
    try (SqlSession session = factory.openSession(false)) {
      final MatchingDao mdao = new MatchingDao(session);
      
      processForeignChildren((tm, t) -> {
        Name parent = foreignChildrenParents.get(t.getId());
        List<Taxon> matches = mdao.matchSector(parent, sector.getKey());
        if (matches.isEmpty()) {
          LOG.warn("{} with parent {} in sector {} cannot be rematched", t.getName(), parent, sector.getKey());
        } else {
          if (matches.size() > 1) {
            LOG.warn("{} with parent {} in sector {} matches {} times - pick first {}", t.getName(), parent, sector.getKey(), matches.size(), matches.get(0));
          }
          t.setParentId(matches.get(0).getId());
          tm.update(t);
        }
      });
    }
  }
  
  private void processForeignChildren(BiConsumer<TaxonMapper, Taxon> processor) {
    int counter = 0;
    try (SqlSession session = factory.openSession(ExecutorType.BATCH, false)) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      for (Taxon t : foreignChildren) {
        processor.accept(tm, t);
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
        List<Taxon> matches = mdao.matchSector(s.getTarget(), sector.getKey());
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
         TreeCopyHandler treeHandler = new TreeCopyHandler(factory, user, sector, state, decisions)
    ){
      NameUsageMapper um = session.getMapper(NameUsageMapper.class);
      LOG.info("Traverse taxon tree, blocking {} nodes", blockedIds.size());
      um.processTree(datasetKey, null, sector.getSubject().getId(), blockedIds, null, true,false, treeHandler);
    }
  }
  
  private void deleteOld() {
    int count;
    try (SqlSession session = factory.openSession(true)) {
      NameUsageMapper um = session.getMapper(NameUsageMapper.class);
      count = um.deleteBySector(catalogueKey, sector.getKey());
      LOG.info("Deleted {} existing taxa with their synonyms and related information from sector {}", count, sector.getKey());
  
      NameMapper nm = session.getMapper(NameMapper.class);
      count = nm.deleteBySector(catalogueKey, sector.getKey());
      LOG.info("Deleted {} names from sector {}", count, sector.getKey());
  
      ReferenceMapper rm = session.getMapper(ReferenceMapper.class);
      count = rm.deleteBySector(catalogueKey, sector.getKey());
      LOG.info("Deleted {} references from sector {}", count, sector.getKey());
    }
  }
  
}
