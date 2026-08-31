package life.catalogue;

import life.catalogue.api.model.Dataset;
import life.catalogue.common.Managed;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.dao.JobDao;
import life.catalogue.dao.UserCrudDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.dw.jersey.filter.SingleDatasetRewriteFilter;
import life.catalogue.dw.managed.ManagedUtils;
import life.catalogue.es.EsUtil;
import life.catalogue.es.indexing.NameUsageIndexService;
import life.catalogue.es.indexing.NameUsageIndexServiceEs;
import life.catalogue.matching.UsageMatcher;
import life.catalogue.matching.UsageMatcherFactory;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.resources.JobResource;
import life.catalogue.resources.matching.FixedNameUsageMatchingResource;
import life.catalogue.resources.matching.openrefine.ReconciliationResource;

import org.apache.ibatis.session.SqlSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.codahale.metrics.health.HealthCheck;

import io.dropwizard.core.setup.Environment;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import jakarta.ws.rs.NotFoundException;

/**
 * The "CLB release in a box" bundle: a single, self contained server for exactly one COL release.
 *
 * It is the read only server plus name matching and OpenRefine reconciliation, backed by a small bundled
 * Postgres that holds just that release and a bundled Elasticsearch. Everything is reused as is - the read
 * API comes from {@link WsROServer}, the matcher from the prebuilt store that {@code BundleBuildCmd} ships,
 * and requests without a dataset key are rewritten to the single release by
 * {@link SingleDatasetRewriteFilter} rather than by forking any resource.
 *
 * Unlike the other apps Elasticsearch is mandatory here: there is no pass through degradation, and if the
 * index holds no documents on startup the release is indexed from the bundle's own Postgres.
 */
public class WsBundleServer extends WsROServer<WsBundleServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(WsBundleServer.class);

  private NameIndex nameIndex;
  private UsageMatcherFactory matcherFactory;
  private final EsIndexBootstrap esBootstrap = new EsIndexBootstrap();

  public static void main(final String[] args) throws Exception {
    SLF4JBridgeHandler.install();
    new WsBundleServer().run(args);
  }

  @Override
  public String getName() {
    return "ChecklistBankBundle";
  }

  /**
   * A bundle always ships elastic, so a missing or empty es section is a configuration error, not a reason
   * to silently serve empty search results.
   */
  @Override
  protected boolean esRequired() {
    return true;
  }

  @Override
  protected NameUsageIndexService buildIndexService(WsBundleServerConfig cfg, Environment env) {
    return new NameUsageIndexServiceEs(esClient, cfg.es, cfg.normalizer.scratchDir("nuproc"), getSqlSessionFactory());
  }

  /**
   * The bundle has its own postgres, so its jobs can be persisted without clashing with another instance
   * over the globally scoped JobMapper.cancelStale().
   */
  @Override
  protected JobExecutor buildJobExecutor(WsBundleServerConfig cfg, Environment env) throws Exception {
    cfg.job.mkdirs();
    var udao = new UserCrudDao(getSqlSessionFactory(), env.getValidator());
    return new JobExecutor(cfg.job, env.metrics(), null, udao, new JobDao(getSqlSessionFactory()));
  }

  @Override
  protected void registerAdditional(WsBundleServerConfig cfg, Environment env, JerseyEnvironment j) throws Exception {
    cfg.mkdirs();
    final Dataset release = readRelease(cfg.releaseKey);
    LOG.info("Serving release {} - {}", cfg.releaseKey, release.getTitle());

    // names index against the bundle's own postgres, catching up from the shipped store
    nameIndex = NameIndexFactory.build(cfg.namesIndex, getSqlSessionFactory(), AuthorshipNormalizer.INSTANCE);
    env.lifecycle().manage(ManagedUtils.from((Managed) nameIndex));

    matcherFactory = new UsageMatcherFactory(cfg.matching, nameIndex, getSqlSessionFactory(), jobExecutor);
    // managed so the stores are closed on shutdown and a missing or stale store is rebuilt from the
    // bundle's own database - the shipped one is normally neither
    env.lifecycle().manage(ManagedUtils.from((Managed) matcherFactory));

    // keyless routing. Registered before the resources so the priority ordering against the alias filter is explicit.
    j.register(new SingleDatasetRewriteFilter(cfg.releaseKey));

    // matching: the global, already keyless /match/nameusage with streaming bulk matching, so an offline
    // bundle needs neither credentials nor a download server for its results. Taken from the factory so
    // there is exactly one open store, shared with reconciliation.
    UsageMatcher matcher = matcherFactory.get(cfg.releaseKey);
    if (matcher == null) {
      throw new IllegalStateException("No matcher store for release " + cfg.releaseKey + " in "
        + cfg.matching.storageDir + ". The bundle data volume is incomplete.");
    }
    j.register(new FixedNameUsageMatchingResource(cfg.matching, release, matcher));

    // OpenRefine reconciliation, dataset scoped and reached keyless through the rewrite filter
    j.register(new ReconciliationResource(cfg.matching, suggestService, getSqlSessionFactory(), matcherFactory,
      cfg.getApiUri(), cfg.clbURI));

    // so a matcher rebuild or any other background job can be followed
    j.register(new JobResource(cfg.job, jobExecutor, new JobDao(getSqlSessionFactory())));

    // elastic: create the index if needed and fill it from the bundled postgres on first boot
    esBootstrap.init(cfg, this);
    env.lifecycle().manage(ManagedUtils.from(esBootstrap));
    env.healthChecks().register("bundle-index", esBootstrap.healthCheck());
  }

  private Dataset readRelease(int key) {
    try (SqlSession session = getSqlSessionFactory().openSession()) {
      Dataset d = session.getMapper(DatasetMapper.class).get(key);
      if (d == null) {
        throw new NotFoundException("The bundle release " + key + " does not exist in the bundled database");
      }
      return d;
    }
  }

  /**
   * Creates the elastic index if it is missing and indexes the single release into it when it holds no
   * documents yet. The indexing runs on a daemon thread so the server comes up immediately; until it is
   * done the {@code bundle-index} health check is unhealthy, which is what a compose healthcheck waits on.
   */
  static class EsIndexBootstrap implements Managed {
    private WsBundleServerConfig cfg;
    private WsBundleServer app;
    private volatile String state = "not started";
    private volatile boolean ready = false;
    private volatile Exception error;
    private Thread thread;

    void init(WsBundleServerConfig cfg, WsBundleServer app) {
      this.cfg = cfg;
      this.app = app;
    }

    HealthCheck healthCheck() {
      return new HealthCheck() {
        @Override
        protected Result check() {
          if (error != null) return Result.unhealthy(error);
          return ready ? Result.healthy(state) : Result.unhealthy(state);
        }
      };
    }

    @Override
    public void start() throws Exception {
      final String index = cfg.es.index.name;
      if (!EsUtil.indexExists(app.esClient, index)) {
        LOG.info("Create missing elastic index {}", index);
        EsUtil.createIndex(app.esClient, cfg.es.index);
      }
      if (!cfg.indexOnStart) {
        state = "indexing disabled";
        ready = true;
        return;
      }
      if (EsUtil.count(app.esClient, index) > 0) {
        state = "index already populated";
        ready = true;
        return;
      }
      state = "indexing release " + cfg.releaseKey;
      thread = new Thread(() -> {
        try {
          LOG.info("Empty elastic index {}. Index release {} from the bundled database", index, cfg.releaseKey);
          var stats = app.indexService.indexDataset(cfg.releaseKey);
          state = "indexed release " + cfg.releaseKey + ": " + stats;
          ready = true;
          LOG.info("Bundle ready. {}", state);
        } catch (Exception e) {
          LOG.error("Failed to index release {} into elastic", cfg.releaseKey, e);
          error = e;
        }
      }, "bundle-es-index");
      thread.setDaemon(true);
      thread.start();
    }

    @Override
    public void stop() throws Exception {
      if (thread != null) {
        thread.interrupt();
      }
    }

    @Override
    public boolean hasStarted() {
      return ready;
    }
  }

  @Override
  protected void onFatalError(Throwable t) {
    LOG.error("Fatal startup error", t);
    System.exit(1);
  }
}
