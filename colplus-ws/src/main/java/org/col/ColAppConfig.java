package org.col;

import io.dropwizard.Configuration;
import org.col.db.PgConfig;

public class ColAppConfig extends Configuration {
  public PgConfig db = new PgConfig();
}
