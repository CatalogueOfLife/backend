package org.col.assembly;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.ColUser;
import org.col.api.model.Sector;
import org.col.api.model.SectorImport;
import org.col.db.mapper.NameUsageMapper;
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
                      BiConsumer<SectorRunnable, Exception> errorCallback, ColUser user) {
    super(sectorKey, factory, indexService, successCallback, errorCallback, user);
  }
  
  @Override
  void doWork() {
    state.setState( SectorImport.State.DELETING);
    deleteSector(sector.getKey());
    
    state.setState( SectorImport.State.INDEXING);
    updateSearchIndex();
    
    state.setState( SectorImport.State.FINISHED);
  }
  
  private void deleteSector(final int sectorKey) {
    visitedSectors.add(sectorKey);
    // first make sure we have no more children
    for (Sector cs : childSectors) {
      if (!visitedSectors.contains(cs.getKey())) {
        deleteSector(cs.getKey());
      }
    }
    try (SqlSession session = factory.openSession(true)) {
      NameUsageMapper um = session.getMapper(NameUsageMapper.class);
      int count = um.deleteBySector(catalogueKey, sectorKey);
      LOG.info("Deleted {} existing taxa with their synonyms and related information from sector {}", count, sectorKey);
    
      session.getMapper(SectorMapper.class).delete(sectorKey);
      LOG.info("Deleted sector {}", sectorKey);
    }
    LOG.info("Deleted {} sectors in total", visitedSectors.size());
  }
  
  private void updateSearchIndex() {
    for (int sKey : visitedSectors) {
      indexService.deleteSector(sector.getKey());
      LOG.info("Removed sector {} from search index", sKey);
    }
    LOG.info("Removed {} sectors from the search index", visitedSectors.size());
  }
  
}
