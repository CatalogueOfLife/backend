package org.col;

import io.dropwizard.Configuration;
import org.col.db.PgConfig;

/**
 * Base DW configuration class for all apps that need access to the postgres db & mybatis.
 */
public class PgAppConfig extends Configuration {
  public PgConfig db = new PgConfig();
}
