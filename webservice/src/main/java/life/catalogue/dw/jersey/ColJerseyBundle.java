package life.catalogue.dw.jersey;

import life.catalogue.WsServerConfig;
import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DatasetListener;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.cache.LatestDatasetKeyCacheImpl;
import life.catalogue.dw.jersey.exception.IllegalArgumentExceptionMapper;
import life.catalogue.dw.jersey.filter.*;
import life.catalogue.dw.jersey.provider.EnumParamConverterProvider;
import life.catalogue.dw.jersey.writers.BufferedImageBodyWriter;

import org.apache.ibatis.session.SqlSessionFactory;

import io.dropwizard.core.ConfiguredBundle;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;

/**
 * Various custom jersey providers bundled together for CoL.
 */
public class ColJerseyBundle implements ConfiguredBundle<WsServerConfig>, DatasetListener {

  private DatasetKeyRewriteFilter lrFilter;
  private CacheControlResponseFilter ccFilter;
  private final LatestDatasetKeyCache cache = new LatestDatasetKeyCacheImpl(null); // we add the factory later - it is not available when we run the bundle!

  @Override
  public void initialize(Bootstrap<?> bootstrap) {
  
  }

  @Override
  public void run(WsServerConfig cfg, Environment env) throws Exception {
    // param converters
    env.jersey().packages(EnumParamConverterProvider.class.getPackage().getName());
    
    // response and request filters
    env.jersey().packages(CreatedResponseFilter.class.getPackage().getName());
    lrFilter = new DatasetKeyRewriteFilter(cache);
    env.jersey().register(lrFilter);
    ccFilter = new CacheControlResponseFilter();
    env.jersey().register(ccFilter);
    env.jersey().register(new DeprecatedWarningResponseFilter(cfg.legacy));
    env.jersey().register(new DelayRequestFilter(cfg.legacy.delay));

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

  @Override
  public void datasetChanged(DatasetChanged d){
    if (d.obj!=null && d.obj.getOrigin().isRelease()) {
      if (d.obj.getSourceKey() != null) {
        // refresh the latest release (candidate) of the source project
        cache.refresh(d.obj.getSourceKey());
      }
    }
  }
}
