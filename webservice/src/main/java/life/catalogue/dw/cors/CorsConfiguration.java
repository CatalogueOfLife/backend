package life.catalogue.dw.cors;


import javax.annotation.Nullable;

public class CorsConfiguration {

  @Nullable
  public String methods = "OPTIONS, HEAD, GET, POST, PUT, DELETE";

  @Nullable
  public String headers = "Authorization, Content-Type, Accept-Language, User-Agent, Referer";

  @Nullable
  public String exposedHeaders = null;

  public int maxAge = -1;

}