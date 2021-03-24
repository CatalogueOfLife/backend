package life.catalogue.dw.metrics;


import info.ganglia.gmetric4j.gmetric.GMetric;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import life.catalogue.dw.cors.CorsFilter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Bundle that sets up a ganglia reporter for dropwizard metrics.
 * If no host is given no reporter is created.
 */
public class GangliaBundle implements ConfiguredBundle<GangliaBundleConfiguration> {
  
  private static final Logger LOG = LoggerFactory.getLogger(GangliaBundle.class);

  @Override
  public void run(GangliaBundleConfiguration cfg, Environment env) throws Exception {
    final GangliaConfiguration gf = cfg.getGangliaConfiguration();

    if (gf.host != null && gf.port > 0) {
      try {
        final GMetric ganglia = new GMetric(gf.host, gf.port, GMetric.UDPAddressingMode.MULTICAST, gf.ttl);
        final GangliaReporter reporter = GangliaReporter.forRegistry(env.metrics())
          .convertRatesTo(TimeUnit.SECONDS)
          .convertDurationsTo(TimeUnit.MILLISECONDS)
          .build(ganglia);
        reporter.start(1, TimeUnit.MINUTES);
        LOG.info("Reporting to ganglia at {}:{}", gf.host, gf.port);
      } catch (IOException e) {
        LOG.warn("Failed to setup ganglia reporting at {}:{}", gf.host, gf.port, e);
      }
    }
  }
  
  @Override
  public void initialize(Bootstrap<?> bootstrap) {
    //Do nothing
  }
  
}