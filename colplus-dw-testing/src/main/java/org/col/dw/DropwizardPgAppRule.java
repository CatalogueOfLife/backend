package org.col.dw;

import java.util.List;
import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import io.dropwizard.Application;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.col.db.EmbeddedColPg;
import org.col.db.PgConfig;
import org.col.db.PgSetupRule;

/**
 * An adaptation of the generic DropwizardAppRule that can be used as a junit class rule
 * to create integration tests against a running dropwizard instance.
 *
 * DropwizardPgAppRule spins up an embedded postgres server
 * and updates the PgConfig with the matching config parameters to access it via the MyBatisModule.
 */
public class DropwizardPgAppRule<C extends PgAppConfig> extends DropwizardAppRule<C> {

  private static final EmbeddedColPg pg = new EmbeddedColPg();

  public DropwizardPgAppRule(Class<? extends Application<C>> applicationClass, @Nullable String configPath,
                             ConfigOverride... configOverrides) {
    super(applicationClass, configPath, setupPg(configOverrides));
  }

  static ConfigOverride[] setupPg(ConfigOverride... configOverrides) {
    pg.start();
    PgConfig cfg = pg.getCfg();
    PgSetupRule.initDb(cfg);

    List<ConfigOverride> overrides = Lists.newArrayList(configOverrides);
    overrides.add(ConfigOverride.config("db.host", cfg.host));
    overrides.add(ConfigOverride.config("db.port", String.valueOf(cfg.port)));
    overrides.add(ConfigOverride.config("db.database", cfg.database));
    overrides.add(ConfigOverride.config("db.user", cfg.user));
    overrides.add(ConfigOverride.config("db.password", cfg.password));
    return overrides.toArray(new ConfigOverride[0]);
  }

  @Override
  protected void after() {
    pg.stop();
    super.after();
  }
}
