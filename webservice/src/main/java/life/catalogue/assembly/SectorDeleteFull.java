package life.catalogue.assembly;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DSIDValue;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.dao.SectorDao;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.es.NameUsageIndexService;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deletes a sector, all its data and recursively deletes also all included, nested sectors!
 */
public class SectorDeleteFull extends SectorRunnable {
  private static final Logger LOG = LoggerFactory.getLogger(SectorDeleteFull.class);
  private final Set<Integer> visitedSectors = new HashSet<>();
  
  public SectorDeleteFull(DSID<Integer> sectorKey, SqlSessionFactory factory, NameUsageIndexService indexService,
                          SectorDao dao, SectorImportDao sid, Consumer<SectorRunnable> successCallback,
                          BiConsumer<SectorRunnable, Exception> errorCallback, User user) throws IllegalArgumentException {
    super(sectorKey, false, false, factory, indexService, dao, sid, successCallback, errorCallback, user);
  }

  @Override
  void doWork() throws Exception {
    state.setState( ImportState.DELETING);
    // do a recursive delete to make sure we have no more children
    for (Sector cs : childSectors) {
      deleteSectorRecursively(cs);
    }
    deleteSector(sectorKey);
    LOG.info("Deleted {} sectors in total", visitedSectors.size());
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
      dao.deleteSector(sectorKey, sectorKey != this.sectorKey);
    }
  }

  @Override
  void doMetrics() throws Exception {
    // we don't remove any sector metric anymore to avoid previous releases to be broken
    // see https://github.com/CatalogueOfLife/backend/issues/986

    //final DSIDValue<Integer> key = DSID.copy(sectorKey);
    //for (int sKey : visitedSectors) {
    //  // remove metric files
    //  try {
    //    sid.deleteAll(key.id(sKey));
    //  } catch (IOException e) {
    //    LOG.error("Failed to delete metrics files for sector {}", key, e);
    //  }
    //  LOG.info("Removed file metrics for sector {}", key);
    //}
    //LOG.info("Removed file metrics for {} sectors", visitedSectors.size());
  }

  @Override
  void updateSearchIndex() {
    final DSIDValue<Integer> key = DSID.copy(sectorKey);
    for (int sKey : visitedSectors) {
      indexService.deleteSector(key.id(sKey));
      LOG.info("Removed sector {} from search index", key);
    }
    LOG.info("Removed {} sectors from the search index", visitedSectors.size());
  }
  
}
