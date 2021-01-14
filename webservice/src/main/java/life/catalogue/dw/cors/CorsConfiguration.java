package life.catalogue.dw.cors;


import javax.annotation.Nullable;

public class CorsConfiguration {

  @Nullable
  public String methods = "OPTIONS, HEAD, GET, POST, PUT, DELETE";

  @Nullable
  public String headers = "Authorization, Content-Type, Accept-Language, User-Agent, Referer";

  /**
   * If enabled sets the Vary header to Origin.
   * If the Webservice is exposed directly set this to true.
   * If an http cache like Varnish is used to control the Vary headers better leave this off!
   */
  public boolean vary = false;

  @Nullable
  public String exposedHeaders = null;

  public int maxAge = -1;

}