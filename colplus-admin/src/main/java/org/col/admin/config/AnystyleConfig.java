package org.col.admin.config;

@Deprecated
public class AnystyleConfig {

  /*
   * Base URL for accessing the anystyle Ruby/Sinatra service. Append a query param like this:
   * "http://localhost:4567?ref=<some_citation>"
   *
   * If empty a mock parser will be used that places everything into the title.
   */
  public String baseUrl;

}
