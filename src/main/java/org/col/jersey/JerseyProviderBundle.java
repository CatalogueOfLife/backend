package org.col.jersey;

import io.dropwizard.Bundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.col.jersey.exception.JsonMappingExceptionMapper;
import org.col.jersey.exception.JsonParseExceptionMapper;
import org.col.jersey.exception.JsonRuntimeMappingExceptionMapper;
import org.col.jersey.exception.QueryParam400Mapper;
import org.col.jersey.filter.CreatedResponseFilter;
import org.col.jersey.provider.PageFactory;

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
    env.jersey().register(JsonMappingExceptionMapper.class);
    env.jersey().register(JsonParseExceptionMapper.class);
    env.jersey().register(JsonRuntimeMappingExceptionMapper.class);
    env.jersey().register(CreatedResponseFilter.class);

    // context providers
    env.jersey().register(new PageFactory.Binder());
  }

}
