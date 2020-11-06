package life.catalogue.dw.jersey;

import com.google.common.eventbus.Subscribe;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import life.catalogue.WsServerConfig;
import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.dw.jersey.exception.IllegalArgumentExceptionMapper;
import life.catalogue.dw.jersey.filter.CacheControlResponseFilter;
import life.catalogue.dw.jersey.filter.CreatedResponseFilter;
import life.catalogue.dw.jersey.filter.DatasetKeyRewriteFilter;
import life.catalogue.dw.jersey.provider.EnumParamConverterProvider;
import life.catalogue.dw.jersey.writers.BufferedImageBodyWriter;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Various custom jersey providers bundled together for CoL.
 */
public class ColJerseyBundle implements ConfiguredBundle<WsServerConfig> {

  DatasetKeyRewriteFilter lrFilter;
  CacheControlResponseFilter ccFilter;

  @Override
  public void initialize(Bootstrap<?> bootstrap) {
  
  }

  @Override
  public void run(WsServerConfig configuration, Environment env) throws Exception {
    // param converters
    env.jersey().packages(EnumParamConverterProvider.class.getPackage().getName());
    
    // response and request filters
    env.jersey().packages(CreatedResponseFilter.class.getPackage().getName());
    lrFilter = new DatasetKeyRewriteFilter();
    env.jersey().register(lrFilter);
    ccFilter = new CacheControlResponseFilter();
    env.jersey().register(ccFilter);

    // exception mappers
    env.jersey().packages(IllegalArgumentExceptionMapper.class.getPackage().getName());
    
    // message writers
    env.jersey().packages(BufferedImageBodyWriter.class.getPackage().getName());
  }

  // needed to populate the session factory in some filters
  public void setSqlSessionFactory(SqlSessionFactory factory) {
    lrFilter.setSqlSessionFactory(factory);
    try (SqlSession session = factory.openSession()){
      ccFilter.addAll(session.getMapper(DatasetMapper.class).keys(DatasetOrigin.RELEASED));
    }
  }

  @Subscribe
  public void datasetChanged(DatasetChanged d){
    if (d.obj!=null && d.obj.getOrigin() == DatasetOrigin.RELEASED) {
      ccFilter.addRelease(d.key);
      if (d.obj.getSourceKey() != null) {
        // refresh the latest release (candidate) of the source project
        lrFilter.refresh(d.obj.getSourceKey());
      }
    }
  }
}
