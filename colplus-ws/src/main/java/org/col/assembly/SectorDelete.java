package org.col.assembly;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.ColUser;
import org.col.api.model.Sector;
import org.col.api.model.SectorImport;
import org.col.db.mapper.NameUsageMapper;
import org.col.db.mapper.SectorImportMapper;
import org.col.db.mapper.SectorMapper;
import org.col.es.NameUsageIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
      deleteSectorRecursively(cs.getKey());
    }
    deleteSector(sector.getKey());
    LOG.info("Deleted {} sectors in total", visitedSectors.size());
    
    state.setState( SectorImport.State.INDEXING);
    updateSearchIndex();
    
    state.setState( SectorImport.State.FINISHED);
  }
  
  @Override
  void finalWork() {
    // nothing, sector is gone
  }
  
  private void deleteSectorRecursively(final int sectorKey) {
    if (!visitedSectors.contains(sectorKey)) {
      Set<Integer> childSectors;
      try (SqlSession session = factory.openSession(true)) {
        SectorMapper sm = session.getMapper(SectorMapper.class);
        childSectors = sm.listChildSectors(sectorKey).stream()
            .map(Sector::getKey)
            .collect(Collectors.toSet());
      }
      for (Integer sk : childSectors) {
        deleteSectorRecursively(sk);
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
        NameUsageMapper um = session.getMapper(NameUsageMapper.class);
        int count = um.deleteBySector(catalogueKey, sectorKey);
        String sectorType = sectorKey == ((int) sector.getKey()) ? "sector" : "subsector";
        LOG.info("Deleted {} existing taxa with their synonyms and related information from {} {}", count, sectorType, sectorKey);
      
        session.getMapper(SectorImportMapper.class).delete(sectorKey);
        session.getMapper(SectorMapper.class).delete(sectorKey);
        LOG.info("Deleted {} {}", sectorType, sectorKey);
      }
    }
  }

  private void updateSearchIndex() {
    for (int sKey : visitedSectors) {
      indexService.deleteSector(sector.getKey());
      LOG.info("Removed sector {} from search index", sKey);
    }
    LOG.info("Removed {} sectors from the search index", visitedSectors.size());
  }
  
}
