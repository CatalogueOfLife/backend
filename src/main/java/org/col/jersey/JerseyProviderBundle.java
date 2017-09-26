package org.col.jersey;

import io.dropwizard.Bundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

/**
 * Various custom jersey providers bundled together for CoL.
 */
public class JerseyProviderBundle implements Bundle {

  @Override
  public void initialize(Bootstrap<?> bootstrap) {

  }

  @Override
  public void run(Environment env) {
    env.jersey().register(QueryParam400Mapper.class);
    env.jersey().register(CreatedResponseFilter.class);
  }

}
