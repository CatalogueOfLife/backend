package org.col;

import javax.validation.constraints.NotNull;

import org.col.dw.PgAppConfig;
import org.col.es.EsConfig;


public class WsServerConfig extends PgAppConfig {
  
  public EsConfig es = new EsConfig();
  
  @NotNull
  public String raml = "https://sp2000.github.io/colplus/api/api.html";
  
}
