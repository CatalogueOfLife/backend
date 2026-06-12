package life.catalogue.assembly;

import life.catalogue.api.model.DSID;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.common.id.ShortUUID;
import life.catalogue.dao.EstimateDao;
import life.catalogue.dao.SectorDao;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.es.indexing.NameUsageIndexService;
import life.catalogue.event.EventBroker;
import life.catalogue.matching.IdentifierScopeResolver;
import life.catalogue.matching.UsageMatcher;
import life.catalogue.matching.UsageMatcherFactory;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.release.UsageIdGen;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class SyncFactory {
  private static final Logger LOG = LoggerFactory.getLogger(SyncFactory.class);

  private final SectorDao sd;
  private final SectorImportDao sid;
  private final NameIndex nameIndex;
  private final EstimateDao estimateDao;
  private final UsageMatcherFactory matcherFactory;
  private final SqlSessionFactory factory;
  private final NameUsageIndexService indexService;
  private final EventBroker bus;
  private final IdentifierScopeResolver scopeResolver;
  private final LatestDatasetKeyCache latestKeyCache;

  public SyncFactory(SqlSessionFactory factory, UsageMatcherFactory matcherFactory, NameIndex nameIndex,
                     SectorDao sd, SectorImportDao sid, EstimateDao estimateDao,
                     NameUsageIndexService indexService, EventBroker bus,
                     @Nullable IdentifierScopeResolver scopeResolver,
                     LatestDatasetKeyCache latestKeyCache) {
    this.bus = bus;
    this.sd = sd;
    this.sid = sid;
    this.nameIndex = nameIndex;
    this.estimateDao = estimateDao;
    this.factory = factory;
    this.indexService = indexService;
    this.matcherFactory = matcherFactory;
    this.scopeResolver = scopeResolver;
    this.latestKeyCache = latestKeyCache == null ? LatestDatasetKeyCache.passThru() : latestKeyCache;
  }

  /**
   * Creates a new sync into a project dataset using a direct postgres matcher with the tree merge handlers write batch session.
   */
  public SectorSync project(DSID<Integer> sectorKey, @Nullable SyncCounter counter, int user) throws IllegalArgumentException {
    return new SectorSync(sectorKey, sectorKey.getDatasetKey(), true, null, factory, nameIndex,
      supplyPgMatcher(sectorKey.getDatasetKey()), bus, indexService, sd, sid, estimateDao,
      counter, ShortUUID.ID_GEN, ShortUUID.ID_GEN, UsageIdGen.RANDOM_SHORT_UUID, scopeResolver, user);
  }

  private Function<SqlSession, UsageMatcher> supplyPgMatcher(int datasetKey) {
    return sess -> matcherFactory.postgres(datasetKey, sess);
  }

  /**
   * Creates a new sync into a release dataset reusing the given matcher.
   */
  public SectorSync release(DSID<Integer> sectorKey, int releaseDatasetKey, @Nullable TreeMergeHandlerConfig cfg, UsageMatcher matcher,
                            Supplier<String> nameIdGen, Supplier<String> typeMaterialIdGen, UsageIdGen usageIdGen, int user) throws IllegalArgumentException {
    Preconditions.checkArgument(releaseDatasetKey == matcher.getDatasetKey(), "Matcher and release dataset key must be the same");
    return new SectorSync(sectorKey, releaseDatasetKey, false, cfg, factory, nameIndex, session -> matcher, bus, indexService, sd, sid, estimateDao,
      null, nameIdGen, typeMaterialIdGen, usageIdGen, scopeResolver, user);
  }

  /**
   * Creates a new hierarchy sync that delegates the higher classification of a project to a target taxonomy.
   * The sector must be in {@link life.catalogue.api.model.Sector.Mode#HIERARCHY} mode.
   */
  public HierarchySync hierarchy(DSID<Integer> sectorKey, @Nullable SyncCounter counter, int user) throws IllegalArgumentException {
    return new HierarchySync(sectorKey, factory, nameIndex, supplyPgMatcher(sectorKey.getDatasetKey()), latestKeyCache,
      bus, indexService, sd, sid, counter, scopeResolver, user);
  }

  public SectorDelete delete(DSID<Integer> sectorKey, @Nullable SyncCounter counter, int user) throws IllegalArgumentException {
    return new SectorDelete(sectorKey, factory, indexService, sd, sid, bus, counter, user);
  }

  public SectorDeleteFull deleteFull(DSID<Integer> sectorKey, @Nullable SyncCounter counter, int user) throws IllegalArgumentException {
    return new SectorDeleteFull(sectorKey, factory, indexService, bus, sd, sid, counter, user);
  }

  public void assertComponentsOnline() {
    nameIndex.assertOnline();
  }
}
