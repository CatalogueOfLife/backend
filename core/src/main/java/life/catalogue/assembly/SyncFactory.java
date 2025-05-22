package life.catalogue.assembly;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.common.id.ShortUUID;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.EstimateDao;
import life.catalogue.dao.SectorDao;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.event.EventBroker;
import life.catalogue.matching.UsageMatcherGlobal;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.release.UsageIdGen;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncFactory {
  private static final Logger LOG = LoggerFactory.getLogger(SyncFactory.class);

  private final SectorDao sd;
  private final SectorImportDao sid;
  private final NameIndex nameIndex;
  private final EstimateDao estimateDao;
  private final UsageMatcherGlobal matcher;
  private final SqlSessionFactory factory;
  private final NameUsageIndexService indexService;
  private final EventBroker bus;

  public SyncFactory(SqlSessionFactory factory, NameIndex nameIndex, UsageMatcherGlobal matcher,
                     SectorDao sd, SectorImportDao sid, EstimateDao estimateDao,
                     NameUsageIndexService indexService, EventBroker bus) {
    this.bus = bus;
    this.sd = sd;
    this.sid = sid;
    this.nameIndex = nameIndex;
    this.estimateDao = estimateDao;
    this.matcher = matcher;
    this.factory = factory;
    this.indexService = indexService;
  }

  /**
   * Creates a new sync into a project dataset
   */
  public SectorSync project(DSID<Integer> sectorKey, Consumer<SectorRunnable> successCallback, BiConsumer<SectorRunnable, Exception> errorCallback, int user) throws IllegalArgumentException {
    return new SectorSync(sectorKey, sectorKey.getDatasetKey(), true, null, factory, nameIndex, matcher, bus, indexService, sd, sid, estimateDao,
      successCallback, errorCallback, ShortUUID.ID_GEN, ShortUUID.ID_GEN, UsageIdGen.RANDOM_SHORT_UUID, user);
  }

  /**
   * Creates a new sync into a release dataset
   */
  public SectorSync release(Sector sector, int releaseDatasetKey, @Nullable TreeMergeHandlerConfig cfg,
                            Supplier<String> nameIdGen, Supplier<String> typeMaterialIdGen, UsageIdGen usageIdGen, int user) throws IllegalArgumentException {
    // make sure the sector is a project sector, not from a release
    var skey = DSID.of(DatasetInfoCache.CACHE.keyOrProjectKey(sector.getDatasetKey()), sector.getId());
    return new SectorSync(skey, releaseDatasetKey, false, cfg, factory, nameIndex, matcher, bus, indexService, sd, sid, estimateDao,
      x -> {}, (s,e) -> LOG.error("Sector merge {} into release {} failed: {}", sector, releaseDatasetKey, e.getMessage(), e),
      nameIdGen, typeMaterialIdGen, usageIdGen, user);
  }

  public SectorDelete delete(DSID<Integer> sectorKey, Consumer<SectorRunnable> successCallback, BiConsumer<SectorRunnable, Exception> errorCallback, int user) throws IllegalArgumentException {
    return new SectorDelete(sectorKey, factory, matcher, indexService, sd, sid, bus, successCallback, errorCallback, user);
  }

  public SectorDeleteFull deleteFull(DSID<Integer> sectorKey, Consumer<SectorRunnable> successCallback, BiConsumer<SectorRunnable, Exception> errorCallback, int user) throws IllegalArgumentException {
    return new SectorDeleteFull(sectorKey, factory, matcher, indexService, bus, sd, sid, successCallback, errorCallback, user);
  }

  public void assertComponentsOnline() {
    matcher.assertComponentsOnline();
  }
}
