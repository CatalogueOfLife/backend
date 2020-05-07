package life.catalogue.assembly;

import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SectorImport;
import life.catalogue.api.model.User;
import life.catalogue.dao.SectorDao;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.SectorImportMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.es.NameUsageIndexService;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Deletes a sector and all its imports, but keeps synced data of rank above species level by default.
 * Names and taxa of ranks above species are kept, but the sectorKey is removed from all entities that previously belonged to the deleted sector.
 */
public class SectorDelete extends SectorRunnable {
  private static final Logger LOG = LoggerFactory.getLogger(SectorDelete.class);
  private Rank cutoffRank = Rank.SPECIES;

  public SectorDelete(int sectorKey, SqlSessionFactory factory, NameUsageIndexService indexService,
                      Consumer<SectorRunnable> successCallback,
                      BiConsumer<SectorRunnable, Exception> errorCallback, User user) throws IllegalArgumentException {
    super(sectorKey, false, factory, indexService, successCallback, errorCallback, false, user);
  }
  
  @Override
  void doWork() {
    state.setState( SectorImport.State.DELETING);
    deleteSector(sectorKey);
    LOG.info("Removed sector {}, keeping usages above {} level", sectorKey, cutoffRank);
    
    state.setState( SectorImport.State.INDEXING);
    updateSearchIndex();
    
    state.setState( SectorImport.State.FINISHED);
  }

  
  private void deleteSector(int sectorKey) {
    try (SqlSession session = factory.openSession(true)) {
      Sector s = session.getMapper(SectorMapper.class).get(sectorKey);
      if (s == null) {
        throw new IllegalArgumentException("Sector "+sectorKey+" does not exist");
      }
      NameUsageMapper um = session.getMapper(NameUsageMapper.class);
      int count = um.removeSectorKey(catalogueKey, sectorKey);

      LOG.info("Deleted {} existing taxa with their synonyms and related information from sector {}", count, sectorKey);

      // update datasetSectors counts
      SectorDao.incSectorCounts(session, s, -1);
      // remove imports and sector itself
      session.getMapper(SectorImportMapper.class).delete(sectorKey);
      session.getMapper(SectorMapper.class).delete(sectorKey);
      LOG.info("Deleted sector {}", sectorKey);
    }
  }

  private void updateSearchIndex() {
    indexService.indexSector(sector);
    LOG.info("Reindexed sector {} from search index", sectorKey);
  }
  
}
