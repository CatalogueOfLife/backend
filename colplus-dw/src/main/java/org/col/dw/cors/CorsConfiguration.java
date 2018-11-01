package org.col.dw.cors;

import org.hibernate.validator.constraints.NotEmpty;

public class CorsConfiguration {
  public static final String ANY_ORIGIN = "*";
  
  @NotEmpty
  public String allowedOrigins = ANY_ORIGIN;

  public boolean anyOrigin() {
    return ANY_ORIGIN.equals(allowedOrigins);
  }
  
}