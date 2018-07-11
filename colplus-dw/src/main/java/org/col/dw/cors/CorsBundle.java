package org.col.dw.cors;


import java.util.EnumSet;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;

import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static org.eclipse.jetty.servlets.CrossOriginFilter.*;

/**
 * Based on work by Isaac Asensio:
 * https://github.com/isaacasensio/dropwizard-cors/
 */
public class CorsBundle implements ConfiguredBundle<CorsBundleConfiguration> {

  private static final Logger LOGGER = LoggerFactory.getLogger(CorsBundle.class);

  private final String filterName;

  public CorsBundle(String filterName) {
    checkArgument(StringUtils.isNotBlank(filterName), "Filter name cannot be blank or null");
    this.filterName = filterName;
  }

  public CorsBundle() {
    this("cors");
  }

  @Override
  public void run(CorsBundleConfiguration bundleConfiguration, Environment environment) throws Exception {

    final CorsConfiguration configuration = bundleConfiguration.getCorsConfiguration();

    LOGGER.info("Registering CorsBundle with name {} and configuration: {}", filterName, configuration);

    FilterRegistration.Dynamic cors = environment.servlets().addFilter(filterName, CrossOriginFilter.class);
    cors.setInitParameter(ALLOWED_ORIGINS_PARAM, configuration.getAllowedOrigins());
    cors.setInitParameter(ALLOWED_HEADERS_PARAM, configuration.getAllowedHeaders());
    cors.setInitParameter(ALLOWED_METHODS_PARAM, configuration.getAllowedMethods());
    cors.setInitParameter(PREFLIGHT_MAX_AGE_PARAM, String.valueOf(configuration.getMaxAgeInSeconds()));
    cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, configuration.getUrlMapping());

  }

  @Override
  public void initialize(Bootstrap<?> bootstrap) {
    //Do nothing
  }

}