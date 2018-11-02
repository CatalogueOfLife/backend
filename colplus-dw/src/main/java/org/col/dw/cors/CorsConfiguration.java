package org.col.dw.cors;

import org.hibernate.validator.constraints.NotEmpty;

public class CorsConfiguration {
  public static final String ANY_ORIGIN = "*";
  
  @NotEmpty
  public String origins = ANY_ORIGIN;
  
  @NotEmpty
  public String methods = "OPTIONS, GET, POST, PUT, DELETE";
  
  @NotEmpty
  public String headers = "Authorization";

  public boolean anyOrigin() {
    return ANY_ORIGIN.equals(origins);
  }
  
}