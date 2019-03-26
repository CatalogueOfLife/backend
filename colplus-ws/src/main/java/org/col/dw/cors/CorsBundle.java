package org.col.dw.cors;


import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Based on work by Isaac Asensio:
 * https://github.com/isaacasensio/dropwizard-cors/
 */
public class CorsBundle implements ConfiguredBundle<CorsBundleConfiguration> {
  
  private static final Logger LOG = LoggerFactory.getLogger(CorsBundle.class);
  
  private final String filterName;
  
  public CorsBundle(String filterName) {
    checkArgument(StringUtils.isNotBlank(filterName), "Filter name cannot be blank or null");
    this.filterName = filterName;
  }
  
  public CorsBundle() {
    this("cors");
  }
  
  @Override
  public void run(CorsBundleConfiguration cfg, Environment environment) throws Exception {
    
    final CorsConfiguration configuration = cfg.getCorsConfiguration();
    
    LOG.info("Registering CorsBundle with name {} and configuration: {}", filterName, configuration);
    
    environment.jersey().register(new CorsFilter(cfg.getCorsConfiguration()));
  }
  
  @Override
  public void initialize(Bootstrap<?> bootstrap) {
    //Do nothing
  }
  
}