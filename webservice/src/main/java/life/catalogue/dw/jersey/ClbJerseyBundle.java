package life.catalogue.dw.jersey;

import com.google.common.eventbus.Subscribe;

import io.dropwizard.core.ConfiguredBundle;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;

import life.catalogue.WsServerConfig;
import life.catalogue.api.event.DatasetChanged;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.cache.LatestDatasetKeyCacheImpl;
import life.catalogue.dw.jersey.exception.IllegalArgumentExceptionMapper;
import life.catalogue.dw.jersey.filter.*;
import life.catalogue.dw.jersey.provider.EnumParamConverterProvider;
import life.catalogue.dw.jersey.writers.BufferedImageBodyWriter;

import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Various custom jersey providers bundled together for CoL.
 */
public class ClbJerseyBundle implements ConfiguredBundle<WsServerConfig> {

  private final LatestDatasetKeyCache cache = new LatestDatasetKeyCacheImpl(null); // we add the factory later - it is not available when we run the bundle!

  @Override
  public void initialize(Bootstrap<?> bootstrap) {
  
  }

  @Override
  public void run(WsServerConfig cfg, Environment env) throws Exception {
    // response and request filters
    env.jersey().packages(CreatedResponseFilter.class.getPackage().getName());
    env.jersey().register(new CacheControlResponseFilter());
    env.jersey().register(new DatasetKeyRewriteFilter(cache));
    env.jersey().register(new DeprecatedWarningResponseFilter(cfg.support, cfg.sunset));
    env.jersey().register(new DelayRequestFilter(cfg.legacyDelay));

    // param converters
    env.jersey().packages(EnumParamConverterProvider.class.getPackage().getName());

    // exception mappers via @Provides
    env.jersey().packages(IllegalArgumentExceptionMapper.class.getPackage().getName());

    // message writers
    env.jersey().packages(BufferedImageBodyWriter.class.getPackage().getName());
  }

  // needed to populate the session factory in some filters
  public void setSqlSessionFactory(SqlSessionFactory factory) {
    cache.setSqlSessionFactory(factory);
  }

  public LatestDatasetKeyCache getCache() {
    return cache;
  }

  @Subscribe
  public void datasetChanged(DatasetChanged d){
    if (d.obj!=null && d.obj.getOrigin().isRelease()) {
      if (d.obj.getSourceKey() != null) {
        // refresh the latest release (candidate) of the source project
        cache.refresh(d.obj.getSourceKey());
      }
    }
  }
}
