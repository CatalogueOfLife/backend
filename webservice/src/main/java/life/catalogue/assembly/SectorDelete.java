package life.catalogue.assembly;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.dao.FileMetricsSectorDao;
import life.catalogue.dao.SectorDao;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Deletes a sector and all its imports, but keeps synced data of rank above species level by default.
 * Names and taxa of ranks above species are kept, but the sectorKey is removed from all entities that previously belonged to the deleted sector.
 */
public class SectorDelete extends SectorRunnable {
  private static final Logger LOG = LoggerFactory.getLogger(SectorDelete.class);
  private Rank cutoffRank = Rank.SPECIES;
  private final FileMetricsSectorDao fmDao;

  public SectorDelete(DSID<Integer> sectorKey, SqlSessionFactory factory, NameUsageIndexService indexService, FileMetricsSectorDao fmDao,
                      Consumer<SectorRunnable> successCallback,
                      BiConsumer<SectorRunnable, Exception> errorCallback, User user) throws IllegalArgumentException {
    super(sectorKey, false, factory, indexService, successCallback, errorCallback, user);
    this.fmDao = fmDao;
  }
  
  @Override
  void doWork() {
    state.setState( ImportState.DELETING);
    deleteSector(sectorKey);
    LOG.info("Removed sector {}, keeping usages above {} level", sectorKey, cutoffRank);
    
    state.setState( ImportState.INDEXING);
    updateSearchIndex();
    
    state.setState( ImportState.FINISHED);
  }

  
  private void deleteSector(DSID<Integer> sectorKey) {
    try (SqlSession session = factory.openSession(false)) {
      Sector s = session.getMapper(SectorMapper.class).get(sectorKey);
      if (s == null) {
        throw new IllegalArgumentException("Sector "+sectorKey+" does not exist");
      }
      NameUsageMapper um = session.getMapper(NameUsageMapper.class);
      NameMapper nm = session.getMapper(NameMapper.class);
      // cascading delete removes vernacular, distributions, descriptions, media
      List<String> ids = um.deleteBySectorAndRank(sectorKey, Rank.SUBGENUS);
      int delTaxa = ids.size();
      // now also remove the names
      final DSID<String> key = DSID.of(sectorKey.getDatasetKey(), "");
      ids.forEach(nid -> nm.delete(key.id(nid)));
      session.commit();
      // TODO: remove refs and name rels
      LOG.info("Deleted {} taxa and synonyms below genus level from sector {}", delTaxa, sectorKey);

      // remove sector from usages, names, refs & type_material
      int count = um.removeSectorKey(sectorKey);
      session.getMapper(NameMapper.class).removeSectorKey(sectorKey);
      session.getMapper(ReferenceMapper.class).removeSectorKey(sectorKey);
      session.getMapper(TypeMaterialMapper.class).removeSectorKey(sectorKey);
      LOG.info("Mark {} existing taxa with their synonyms and related information to not belong to sector {} anymore", count, sectorKey);

      // update datasetSectors counts
      SectorDao.incSectorCounts(session, s, -1);
      // remove imports and sector itself
      session.getMapper(SectorImportMapper.class).delete(sectorKey);
      session.getMapper(SectorMapper.class).delete(sectorKey);
      session.commit();
      // remove metric files
      try {
        fmDao.deleteAll(sectorKey);
      } catch (IOException e) {
        LOG.error("Failed to delete metrics files for sector {}", sectorKey, e);
      }

      LOG.info("Deleted sector {}", sectorKey);
    }
  }

  private void updateSearchIndex() {
    indexService.indexSector(sector);
    LOG.info("Reindexed sector {} from search index", sectorKey);
  }
  
}
