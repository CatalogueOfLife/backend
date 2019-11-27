package life.catalogue.dw.cors;

import org.hibernate.validator.constraints.NotEmpty;

public class CorsConfiguration {
  public static final String ANY_ORIGIN = "*";
  
  @NotEmpty
  public String origins = ANY_ORIGIN;
  
  @NotEmpty
  public String methods = "OPTIONS, HEAD, GET, POST, PUT, DELETE";
  
  @NotEmpty
  public String headers = "Authorization, Content-Type, Accept-Language";
  
  public boolean anyOrigin() {
    return ANY_ORIGIN.equals(origins);
  }
  
}