package life.catalogue.dw.jersey;

import io.dropwizard.Bundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import life.catalogue.dw.jersey.exception.IllegalArgumentExceptionMapper;
import life.catalogue.dw.jersey.filter.CreatedResponseFilter;
import life.catalogue.dw.jersey.filter.DatasetKeyRequestFilter;
import life.catalogue.dw.jersey.provider.EnumParamConverterProvider;
import life.catalogue.dw.jersey.writers.BufferedImageBodyWriter;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Various custom jersey providers bundled together for CoL.
 */
public class ColJerseyBundle implements Bundle {

  DatasetKeyRequestFilter filter;

  @Override
  public void initialize(Bootstrap<?> bootstrap) {
  
  }
  
  @Override
  public void run(Environment env) {
    // param converters
    env.jersey().packages(EnumParamConverterProvider.class.getPackage().getName());
    
    // response and request filters
    env.jersey().packages(CreatedResponseFilter.class.getPackage().getName());
    filter = new DatasetKeyRequestFilter();
    env.jersey().register(filter);

    // exception mappers
    env.jersey().packages(IllegalArgumentExceptionMapper.class.getPackage().getName());
    
    // message writers
    env.jersey().packages(BufferedImageBodyWriter.class.getPackage().getName());
  }

  // needed to populate the session factory in some filters
  public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
    filter.setSqlSessionFactory(sqlSessionFactory);
  }
}
