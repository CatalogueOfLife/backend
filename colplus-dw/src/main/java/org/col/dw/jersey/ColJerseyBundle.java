package org.col.dw.jersey;

import io.dropwizard.Bundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.col.dw.jersey.exception.IllegalArgumentExceptionMapper;
import org.col.dw.jersey.filter.CreatedResponseFilter;
import org.col.dw.jersey.writers.BufferedImageBodyWriter;
import org.col.dw.jersey.provider.EnumParamConverterProvider;

/**
 * Various custom jersey providers bundled together for CoL.
 */
public class ColJerseyBundle implements Bundle {

  @Override
  public void initialize(Bootstrap<?> bootstrap) {

  }

  @Override
  public void run(Environment env) {
    // param converters
    env.jersey().packages(EnumParamConverterProvider.class.getPackage().getName());

    // filter
    env.jersey().packages(CreatedResponseFilter.class.getPackage().getName());

    // exception mapper
    env.jersey().packages(IllegalArgumentExceptionMapper.class.getPackage().getName());
    
    // message writers
    env.jersey().packages(BufferedImageBodyWriter.class.getPackage().getName());
  }

}
