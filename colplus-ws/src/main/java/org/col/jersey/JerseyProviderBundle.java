package org.col.jersey;

import io.dropwizard.Bundle;
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.col.jersey.exception.*;
import org.col.jersey.filter.CreatedResponseFilter;

/**
 * Various custom jersey providers bundled together for CoL.
 */
public class JerseyProviderBundle implements Bundle {

  @Override
  public void initialize(Bootstrap<?> bootstrap) {

  }

  @Override
  public void run(Environment env) {
    // filter
    env.jersey().register(CreatedResponseFilter.class);

    // exception mapper
    env.jersey().register(QueryParam400Mapper.class);
    env.jersey().register(ValidationExceptionMapper.class);
    env.jersey().register(UnsupportedOperationExceptionMapper.class);
    env.jersey().register(IllegalArgumentExceptionMapper.class);
    env.jersey().register(JsonProcessingExceptionMapper.class);
  }

}
