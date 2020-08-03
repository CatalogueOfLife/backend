package life.catalogue.assembly;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DSIDValue;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.dao.SectorDao;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.SectorImportMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.es.NameUsageIndexService;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Deletes a sector, all its data and recursively deletes also all included, nested sectors!
 */
public class SectorDeleteFull extends SectorRunnable {
  private static final Logger LOG = LoggerFactory.getLogger(SectorDeleteFull.class);
  private Set<Integer> visitedSectors = new HashSet<>();
  
  public SectorDeleteFull(DSID<Integer> sectorKey, SqlSessionFactory factory, NameUsageIndexService indexService,
                          Consumer<SectorRunnable> successCallback,
                          BiConsumer<SectorRunnable, Exception> errorCallback, User user) throws IllegalArgumentException {
    super(sectorKey, false, factory, indexService, successCallback, errorCallback, user);
  }
  
  @Override
  void doWork() {
    state.setState( ImportState.DELETING);
    // do a recursive delete to make sure we have no more children
    for (Sector cs : childSectors) {
      deleteSectorRecursively(cs);
    }
    deleteSector(sectorKey);
    LOG.info("Deleted {} sectors in total", visitedSectors.size());
    
    state.setState( ImportState.INDEXING);
    updateSearchIndex();
    
    state.setState( ImportState.FINISHED);
  }
  
  private void deleteSectorRecursively(final DSID<Integer> sectorKey) {
    if (!visitedSectors.contains(sectorKey.getId())) {
      Set<Integer> childSectors;
      try (SqlSession session = factory.openSession(true)) {
        SectorMapper sm = session.getMapper(SectorMapper.class);
        childSectors = sm.listChildSectors(sectorKey).stream()
            .map(Sector::getId)
            .collect(Collectors.toSet());
      }
      final DSIDValue<Integer> key = DSID.copy(sectorKey);
      for (Integer sk : childSectors) {
        deleteSectorRecursively(key.id(sk));
      }
  
      // ready for deletion.
      // Once we reach here we have no more child sectors that could point via parentId to this sector
      deleteSector(sectorKey);
    }
  }
  
  private void deleteSector(DSID<Integer> sectorKey) {
    if (!visitedSectors.contains(sectorKey.getId())) {
      visitedSectors.add(sectorKey.getId());
      try (SqlSession session = factory.openSession(true)) {
        Sector s = session.getMapper(SectorMapper.class).get(sectorKey);
        if (s == null) {
          throw new IllegalArgumentException("Sector "+sectorKey+" does not exist");
        }
        NameUsageMapper um = session.getMapper(NameUsageMapper.class);
        int count = um.deleteBySector(sectorKey);
        String sectorType = sectorKey == this.sectorKey ? "sector" : "subsector";
        LOG.info("Deleted {} existing taxa with their synonyms and related information from {} {}", count, sectorType, sectorKey);
      
        // update datasetSectors counts
        SectorDao.incSectorCounts(session, s, -1);
        
        session.getMapper(SectorImportMapper.class).delete(sectorKey);
        session.getMapper(SectorMapper.class).delete(sectorKey);
        LOG.info("Deleted {} {}", sectorType, sectorKey);
      }
    }
  }

  private void updateSearchIndex() {
    final DSIDValue<Integer> key = DSID.copy(sectorKey);
    for (int sKey : visitedSectors) {
      indexService.deleteSector(key.id(sKey));
      LOG.info("Removed sector {} from search index", key);
    }
    LOG.info("Removed {} sectors from the search index", visitedSectors.size());
  }
  
}
