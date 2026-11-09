package life.catalogue.release;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.OutboxEntry;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.doi.service.DoiConfig;
import life.catalogue.exporter.ExporterConfig;
import life.catalogue.matching.UsageMatcherFactory;
import life.catalogue.outbox.OutboxHandler;
import life.catalogue.outbox.RetryLaterException;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the RELEASE_PUBLISHED commands of the outbox, requested by the dataset update that made a release public.
 * It submits a {@link PublishReleaseJob}, so the work shows up with its log in the job history,
 * and waits while the job executor is stopped.
 */
public class ReleasePublishedHandler implements OutboxHandler {
  private static final Logger LOG = LoggerFactory.getLogger(ReleasePublishedHandler.class);
  private final ReleaseConfig rCfg;
  private final ExporterConfig eCfg;
  private final DoiConfig dCfg;
  private final SqlSessionFactory factory;
  private final UsageMatcherFactory matcherFactory;
  private final CloseableHttpClient httpClient;
  private final JobExecutor executor;

  public ReleasePublishedHandler(ReleaseConfig rCfg, ExporterConfig eCfg, DoiConfig dCfg, SqlSessionFactory factory,
                                 UsageMatcherFactory matcherFactory, CloseableHttpClient httpClient, JobExecutor executor) {
    this.rCfg = rCfg;
    this.eCfg = eCfg;
    this.dCfg = dCfg;
    this.factory = factory;
    this.matcherFactory = matcherFactory;
    this.httpClient = httpClient;
    this.executor = executor;
  }

  @Override
  public void handle(OutboxEntry entry) {
    final int key = entry.getParams().path("releaseKey").asInt(Integer.parseInt(entry.getKey()));
    final Dataset d;
    try (SqlSession session = factory.openSession(true)) {
      d = session.getMapper(DatasetMapper.class).get(key);
    }
    if (d == null || d.getDeleted() != null || d.isPrivat() || !d.getOrigin().isRelease()) {
      LOG.warn("Dataset {} is no public release (anymore), nothing to publish", key);
      return;
    }
    if (!executor.hasStarted()) {
      throw new RetryLaterException("The job executor is not running");
    }
    var job = new PublishReleaseJob(key, entry.getCreatedBy(), rCfg, eCfg, dCfg, factory, matcherFactory, httpClient, executor);
    executor.submit(job);
    LOG.info("Submitted job {} to publish release {}", job.getKey(), key);
  }
}
