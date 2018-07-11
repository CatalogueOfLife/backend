package org.col;

import javax.validation.constraints.NotNull;

import org.col.dw.PgAppConfig;


public class WsServerConfig extends PgAppConfig {

  @NotNull
  public String raml = "https://sp2000.github.io/colplus/api/api.html";

}
