package life.catalogue.assembly;

import life.catalogue.api.model.*;
import life.catalogue.dao.EstimateDao;
import life.catalogue.dao.SectorDao;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.NameIndex;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SyncFactory {
  private static final Logger LOG = LoggerFactory.getLogger(SyncFactory.class);

  private final SectorDao sd;
  private final SectorImportDao sid;
  private final NameIndex nameIndex;
  private final EstimateDao estimateDao;
  private final UsageMatcherGlobal matcher;
  private final SqlSessionFactory factory;
  private final NameUsageIndexService indexService;

  public SyncFactory(SqlSessionFactory factory, NameIndex nameIndex, UsageMatcherGlobal matcher, SectorDao sd, SectorImportDao sid, EstimateDao estimateDao, NameUsageIndexService indexService) {
    this.sd = sd;
    this.sid = sid;
    this.nameIndex = nameIndex;
    this.estimateDao = estimateDao;
    this.matcher = matcher;
    this.factory = factory;
    this.indexService = indexService;
  }

  public SectorSync project(DSID<Integer> sectorKey, Consumer<SectorRunnable> successCallback, BiConsumer<SectorRunnable, Exception> errorCallback, User user) throws IllegalArgumentException {
    return new SectorSync(sectorKey, sectorKey.getDatasetKey(), true, null, factory, nameIndex, matcher, indexService, sd, sid, estimateDao, successCallback, errorCallback, user);
  }

  public SectorSync release(DSID<Integer> sectorKey, int releaseDatasetKey, Taxon incertae, User user) throws IllegalArgumentException {
    return new SectorSync(sectorKey, releaseDatasetKey, false, incertae, factory, nameIndex, matcher, indexService, sd, sid, estimateDao,
      x -> {}, (s,e) -> {LOG.error("Sector merge {} into release {} failed: {}", sectorKey, releaseDatasetKey, e.getMessage(), e);}, user);
  }

  public SectorDelete delete(DSID<Integer> sectorKey, Consumer<SectorRunnable> successCallback, BiConsumer<SectorRunnable, Exception> errorCallback, User user) throws IllegalArgumentException {
    return new SectorDelete(sectorKey, factory, matcher, indexService, sd, sid, successCallback, errorCallback, user);
  }

  public SectorDeleteFull deleteFull(DSID<Integer> sectorKey, Consumer<SectorRunnable> successCallback, BiConsumer<SectorRunnable, Exception> errorCallback, User user) throws IllegalArgumentException {
    return new SectorDeleteFull(sectorKey, factory, matcher, indexService, sd, sid, successCallback, errorCallback, user);
  }
}
