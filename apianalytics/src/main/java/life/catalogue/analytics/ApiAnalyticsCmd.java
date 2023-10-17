package life.catalogue.analytics;

import com.google.common.base.Preconditions;

import life.catalogue.cache.LatestDatasetKeyCacheImpl;
import life.catalogue.command.AbstractMybatisCmd;

import life.catalogue.dw.jersey.filter.DatasetKeyRewriteFilter;

import net.sourceforge.argparse4j.inf.Subparser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Copied API usage metrics from ES to the database for long term storage.
 * It is based on the similar GBIF spring project https://github.com/gbif/api-analytics/
 */
public class ApiAnalyticsCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(ApiAnalyticsCmd.class);
  private static final String ARG_RANGE = "range";
  private ApiAnalyticsDao dao;
  private Duration range;

  public ApiAnalyticsCmd() {
    super("apiAnalytics", false, "Run regular jobs to copy analytics from ES to postgres");
  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    subparser.addArgument("--"+ARG_RANGE)
             .dest(ARG_RANGE)
             .type(Integer.class)
             .required(false)
             .setDefault(1)
             .help("The time range in hours for each analytics aggregation. Defaults to 1 hour");
  }

  @Override
  public void execute() throws Exception {
    Preconditions.checkNotNull(cfg.analytics, "Analytics ES configs missing");

    // setup
    final int hours = ns.getInt(ARG_RANGE);
    range  = Duration.ofHours(hours);
    LOG.info("Analytics range used: {}", range);

    var exec = Executors.newSingleThreadScheduledExecutor();
    try (var client = new LogsClient(cfg.analytics)) {
      var cache = new LatestDatasetKeyCacheImpl(factory);
      var lrFilter = new DatasetKeyRewriteFilter(cache);
      dao = new ApiAnalyticsDao(client, factory, lrFilter);
      // fill gaps since last start?
      dao.fillTimeGap(range);
      // start scheduled analytics job
      LOG.info("Start analytics updater every {}h", hours);
      exec.schedule(new AnalyticsJob(), hours, TimeUnit.HOURS);
    } finally {
      exec.shutdown();
    }
  }

  class AnalyticsJob implements Runnable {

    @Override
    public void run() {
      LocalDateTime end = dao.getCurrentHour();
      LocalDateTime start = end.minus(range);
      try {
        dao.createAnalytics(start, end)
           .ifPresent(a -> LOG.info("Analytics added from {} to {}: {}", start, end, a));
      } catch (Exception ex) {
        LOG.error("Couldn't add analytics from {} to {}", start, end, ex);
      }
    }
  }
}
