package life.catalogue.dw.cors;


import javax.annotation.Nullable;
import javax.validation.constraints.NotEmpty;

public class CorsConfiguration {
  public static final String ANY_ORIGIN = "*";
  
  @NotEmpty
  public String origins = ANY_ORIGIN;
  
  @Nullable
  public String methods = "OPTIONS, HEAD, GET, POST, PUT, DELETE";

  @Nullable
  public String headers = "Authorization, Content-Type, Accept-Language, User-Agent, Referer";

  @Nullable
  public String exposedHeaders = null;

  public int maxAge = -1;

  public boolean anyOrigin() {
    return ANY_ORIGIN.equals(origins);
  }
  
}