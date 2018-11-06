package org.col.dw.jersey.provider;

import io.dropwizard.Bundle;
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.col.dw.jersey.exception.*;
import org.col.dw.jersey.filter.CreatedResponseFilter;
import org.col.dw.jersey.filter.Null404ResponseFilter;

/**
 * Various custom jersey providers bundled together for CoL.
 */
public class JerseyProviderBundle implements Bundle {

  @Override
  public void initialize(Bootstrap<?> bootstrap) {

  }

  @Override
  public void run(Environment env) {
    // param converters
    env.jersey().register(EnumParamConverterProvider.class);
    env.jersey().register(TermParamConverterProvider.class);

    // filter
    env.jersey().register(CreatedResponseFilter.class);
    env.jersey().register(Null404ResponseFilter.class);

    // exception mapper
    env.jersey().register(IllegalArgumentExceptionMapper.class);
    env.jersey().register(JsonProcessingExceptionMapper.class);
    env.jersey().register(NotFoundExceptionMapper.class);
    env.jersey().register(PersistenceExceptionMapper.class);
    env.jersey().register(QueryParam400Mapper.class);
    env.jersey().register(UnsupportedOperationExceptionMapper.class);
    env.jersey().register(ValidationExceptionMapper.class);
    
    // message writers
    env.jersey().register(BufferedImageBodyWriter.class);
  }

}
