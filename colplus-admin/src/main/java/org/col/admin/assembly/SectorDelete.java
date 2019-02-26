package org.col.admin.assembly;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.ColUser;
import org.col.db.mapper.SectorMapper;
import org.col.db.mapper.TaxonMapper;
import org.col.es.NameUsageIndexServiceEs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deletes a sector and all its data
 */
public class SectorDelete extends SectorRunnable {
  private static final Logger LOG = LoggerFactory.getLogger(SectorDelete.class);
  
  
  public SectorDelete(int sectorKey, SqlSessionFactory factory, NameUsageIndexServiceEs indexService,
                      Consumer<SectorRunnable> successCallback,
                      BiConsumer<SectorRunnable, Exception> errorCallback, ColUser user) {
    super(sectorKey, factory, indexService, successCallback, errorCallback, user);
  }
  
  @Override
  void doWork() {
    try (SqlSession session = factory.openSession(true)) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      int count = tm.deleteBySector(catalogueKey, sector.getKey());
      LOG.info("Deleted {} existing taxa with their synonyms and related information from sector {}", count, sector.getKey());
  
      updateSearchIndex();
      
      session.getMapper(SectorMapper.class).delete(sector.getKey());
      LOG.info("Deleted sector {}", sector.getKey());
    }
  }
  
  private void updateSearchIndex() {
    LOG.info("TODO: Update search index");
  }
  
}
