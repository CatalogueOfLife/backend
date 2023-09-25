package life.catalogue.assembly;

import com.google.common.eventbus.EventBus;

import life.catalogue.api.model.*;
import life.catalogue.common.id.ShortUUID;
import life.catalogue.dao.EstimateDao;
import life.catalogue.dao.SectorDao;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.NameIndex;

import life.catalogue.matching.UsageMatcherGlobal;

import life.catalogue.release.XReleaseConfig;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SyncFactory {
  private static final Logger LOG = LoggerFactory.getLogger(SyncFactory.class);

  private final SectorDao sd;
  private final SectorImportDao sid;
  private final NameIndex nameIndex;
  private final EstimateDao estimateDao;
  private final UsageMatcherGlobal matcher;
  private final SqlSessionFactory factory;
  private final NameUsageIndexService indexService;
  private final EventBus bus;

  public SyncFactory(SqlSessionFactory factory, NameIndex nameIndex, UsageMatcherGlobal matcher,
                     SectorDao sd, SectorImportDao sid, EstimateDao estimateDao,
                     NameUsageIndexService indexService, EventBus bus) {
    this.bus = bus;
    this.sd = sd;
    this.sid = sid;
    this.nameIndex = nameIndex;
    this.estimateDao = estimateDao;
    this.matcher = matcher;
    this.factory = factory;
    this.indexService = indexService;
  }

  public SectorSync project(DSID<Integer> sectorKey, Consumer<SectorRunnable> successCallback, BiConsumer<SectorRunnable, Exception> errorCallback, User user) throws IllegalArgumentException {
    return new SectorSync(sectorKey, sectorKey.getDatasetKey(), true, null, factory, nameIndex, matcher, bus, indexService, sd, sid, estimateDao,
      successCallback, errorCallback, ShortUUID.ID_GEN, ShortUUID.ID_GEN, ShortUUID.ID_GEN, user);
  }

  public SectorSync release(DSID<Integer> sectorKey, int releaseDatasetKey, @Nullable TreeMergeHandlerConfig cfg,
                            Supplier<String> nameIdGen, Supplier<String> usageIdGen, Supplier<String> typeMaterialIdGen, User user) throws IllegalArgumentException {
    return new SectorSync(sectorKey, releaseDatasetKey, false, cfg, factory, nameIndex, matcher, bus, indexService, sd, sid, estimateDao,
      x -> {}, (s,e) -> LOG.error("Sector merge {} into release {} failed: {}", sectorKey, releaseDatasetKey, e.getMessage(), e),
      nameIdGen, usageIdGen, typeMaterialIdGen, user);
  }

  public SectorDelete delete(DSID<Integer> sectorKey, Consumer<SectorRunnable> successCallback, BiConsumer<SectorRunnable, Exception> errorCallback, User user) throws IllegalArgumentException {
    return new SectorDelete(sectorKey, factory, matcher, indexService, sd, sid, bus, successCallback, errorCallback, user);
  }

  public SectorDeleteFull deleteFull(DSID<Integer> sectorKey, Consumer<SectorRunnable> successCallback, BiConsumer<SectorRunnable, Exception> errorCallback, User user) throws IllegalArgumentException {
    return new SectorDeleteFull(sectorKey, factory, matcher, indexService, bus, sd, sid, successCallback, errorCallback, user);
  }

  public void assertComponentsOnline() {
    matcher.assertComponentsOnline();
  }
}
