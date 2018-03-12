package org.col;

import org.col.dw.PgAppConfig;

import javax.validation.constraints.NotNull;

public class WsServerConfig extends PgAppConfig {

  @NotNull
  public String raml = "https://sp2000.github.io/colplus/api/api.html";

}
