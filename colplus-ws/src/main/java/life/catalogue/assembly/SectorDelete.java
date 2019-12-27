package life.catalogue.assembly;

import life.catalogue.api.model.ColUser;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SectorImport;
import life.catalogue.dao.SectorDao;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.SectorImportMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.es.name.index.NameUsageIndexService;
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
public class SectorDelete extends SectorRunnable {
  private static final Logger LOG = LoggerFactory.getLogger(SectorDelete.class);
  private Set<Integer> visitedSectors = new HashSet<>();
  
  public SectorDelete(int sectorKey, SqlSessionFactory factory, NameUsageIndexService indexService,
                      Consumer<SectorRunnable> successCallback,
                      BiConsumer<SectorRunnable, Exception> errorCallback, ColUser user) throws IllegalArgumentException {
    super(sectorKey, false, factory, indexService, successCallback, errorCallback, user);
  }
  
  @Override
  void doWork() {
    state.setState( SectorImport.State.DELETING);
    // do a recursive delete to make sure we have no more children
    for (Sector cs : childSectors) {
      deleteSectorRecursively(cs.getDatasetKey(), cs.getKey());
    }
    deleteSector(sectorKey);
    LOG.info("Deleted {} sectors in total", visitedSectors.size());
    
    state.setState( SectorImport.State.INDEXING);
    updateSearchIndex();
    
    state.setState( SectorImport.State.FINISHED);
  }
  
  @Override
  void finalWork() {
    // nothing, sector is gone
  }
  
  private void deleteSectorRecursively(final int catalogueKey, final int sectorKey) {
    if (!visitedSectors.contains(sectorKey)) {
      Set<Integer> childSectors;
      try (SqlSession session = factory.openSession(true)) {
        SectorMapper sm = session.getMapper(SectorMapper.class);
        childSectors = sm.listChildSectors(catalogueKey, sectorKey).stream()
            .map(Sector::getKey)
            .collect(Collectors.toSet());
      }
      for (Integer sk : childSectors) {
        deleteSectorRecursively(catalogueKey, sk);
      }
  
      // ready for deletion.
      // Once we reach here we have no more child sectors that could point via parentId to this sector
      deleteSector(sectorKey);
    }
  }
  
  private void deleteSector(int sectorKey) {
    if (!visitedSectors.contains(sectorKey)) {
      visitedSectors.add(sectorKey);
      try (SqlSession session = factory.openSession(true)) {
        Sector s = session.getMapper(SectorMapper.class).get(sectorKey);
        if (s == null) {
          throw new IllegalArgumentException("Sector "+sectorKey+" does not exist");
        }
        NameUsageMapper um = session.getMapper(NameUsageMapper.class);
        int count = um.deleteBySector(catalogueKey, sectorKey);
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
    for (int sKey : visitedSectors) {
      indexService.deleteSector(sectorKey);
      LOG.info("Removed sector {} from search index", sKey);
    }
    LOG.info("Removed {} sectors from the search index", visitedSectors.size());
  }
  
}
