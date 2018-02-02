package org.col.dw;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.dropwizard.Configuration;
import org.col.dw.cors.CorsBundleConfiguration;
import org.col.dw.cors.CorsConfiguration;
import org.col.db.PgConfig;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

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
