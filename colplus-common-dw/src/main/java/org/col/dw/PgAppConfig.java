package org.col.dw;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.dropwizard.Configuration;
import org.col.db.PgConfig;
import org.col.dw.cors.CorsBundleConfiguration;
import org.col.dw.cors.CorsConfiguration;

/**
 * Base DW configuration class for all apps that need access to the postgres db & mybatis.
 */
public class PgAppConfig extends Configuration implements CorsBundleConfiguration {
  public PgConfig db = new PgConfig();

  @Valid
  @NotNull
  private CorsConfiguration cors = new CorsConfiguration();

  @Override
  @JsonIgnore
  public CorsConfiguration getCorsConfiguration() {
    return cors;
  }
}
