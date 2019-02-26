package org.col.admin.assembly;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.ColUser;
import org.col.api.model.Sector;
import org.col.db.mapper.SectorMapper;
import org.col.db.mapper.TaxonMapper;
import org.col.es.NameUsageIndexServiceEs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deletes a sector, all its data and all recursively delate also all included, nested sectors
 */
public class SectorDelete extends SectorRunnable {
  private static final Logger LOG = LoggerFactory.getLogger(SectorDelete.class);
  private Set<Integer> visitedSectors = new HashSet<>();
  
  public SectorDelete(int sectorKey, SqlSessionFactory factory, NameUsageIndexServiceEs indexService,
                      Consumer<SectorRunnable> successCallback,
                      BiConsumer<SectorRunnable, Exception> errorCallback, ColUser user) {
    super(sectorKey, factory, indexService, successCallback, errorCallback, user);
  }
  
  @Override
  void doWork() {
    deleteSector(sector.getKey());
    updateSearchIndex();
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
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      int count = tm.deleteBySector(catalogueKey, sectorKey);
      LOG.info("Deleted {} existing taxa with their synonyms and related information from sector {}", count, sectorKey);
    
      session.getMapper(SectorMapper.class).delete(sectorKey);
      LOG.info("Deleted sector {}", sectorKey);
    }
  }
  
  private void updateSearchIndex() {
    LOG.info("TODO: Update search index for all {} sectors", visitedSectors.size());
  }
  
}
