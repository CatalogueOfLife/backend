package org.col.dw;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.dropwizard.Configuration;
import io.dropwizard.client.JerseyClientConfiguration;
import org.col.db.PgConfig;
import org.col.dw.auth.AuthenticationProviderFactory;
import org.col.dw.cors.CorsBundleConfiguration;
import org.col.dw.cors.CorsConfiguration;
import org.col.img.ImgConfig;

/**
 * Base DW configuration class for all apps that need access to the postgres db & mybatis.
 */
public class PgAppConfig extends Configuration implements CorsBundleConfiguration {
  
  @Valid
  @NotNull
  public PgConfig db = new PgConfig();
  
  @Valid
  @NotNull
  public AuthenticationProviderFactory auth;
  
  /**
   * Json Web Token used to trust in externally authenticated users.
   */
  @NotNull
  public String jwtKey = "bhc564c76VT-d/722mc";
  
  @Valid
  @NotNull
  public CorsConfiguration cors = new CorsConfiguration();
  
  @Valid
  @NotNull
  public JerseyClientConfiguration client = new JerseyClientConfiguration();
  
  @Valid
  @NotNull
  public ImgConfig img = new ImgConfig();
  
  
  @Override
  @JsonIgnore
  public CorsConfiguration getCorsConfiguration() {
    return cors;
  }
  
}
