package org.col.dw;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.google.common.collect.Lists;
import io.dropwizard.Application;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.col.common.io.PortUtil;
import org.col.common.util.YamlUtils;
import org.col.db.EmbeddedColPg;
import org.col.db.PgConfig;
import org.col.db.PgSetupRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An adaptation of the generic DropwizardAppRule that can be used as a junit class rule
 * to create integration tests against a running dropwizard instance.
 *
 * DropwizardPgAppRule spins up an embedded postgres server
 * and updates the PgConfig with the matching config parameters to access it via the MyBatisModule.
 *
 * It also selects and configures DW to use a free application port.
 */
public class DropwizardPgAppRule<C extends PgAppConfig> extends DropwizardAppRule<C> {
  private static final Logger LOG = LoggerFactory.getLogger(DropwizardPgAppRule.class);
  private static EmbeddedColPg pg;

  public DropwizardPgAppRule(Class<? extends Application<C>> applicationClass,
                             String configPath, ConfigOverride... configOverrides) {
    super(applicationClass, configPath, setupPg(configPath, configOverrides));
  }

  static class PgConfigInApp {
    public PgConfig db = new PgConfig();
  }

  static ConfigOverride[] setupPg(String configPath, ConfigOverride... configOverrides) {
    List<ConfigOverride> overrides = Lists.newArrayList(configOverrides);
    try {
      PgConfigInApp cfg = YamlUtils.read(PgConfigInApp.class, new File(configPath));
      pg = new EmbeddedColPg(cfg.db);
      pg.start();
      PgSetupRule.initDb(cfg.db);

      overrides.add(ConfigOverride.config("db.host", cfg.db.host));
      overrides.add(ConfigOverride.config("db.port", String.valueOf(cfg.db.port)));
      overrides.add(ConfigOverride.config("db.database", cfg.db.database));
      overrides.add(ConfigOverride.config("db.user", cfg.db.user));
      overrides.add(ConfigOverride.config("db.password", cfg.db.password));

    } catch (Exception e) {
      throw new RuntimeException("Failed to read popstgres configuration from " + configPath, e);
    }

    // select free DW port
    try {
      int dwPort = PortUtil.findFreePort();
      int dwPortAdmin = PortUtil.findFreePort();
      LOG.info("Configure DW ports application={}, admin={}", dwPort, dwPortAdmin);
      overrides.add(ConfigOverride.config("server.applicationConnectors[0].port", String.valueOf(dwPort)));
      overrides.add(ConfigOverride.config("server.adminConnectors[0].port", String.valueOf(dwPortAdmin)));
    } catch (IOException e) {
      throw new RuntimeException("Failed to select free Dropwizard application port", e);
    }

    return overrides.toArray(new ConfigOverride[0]);
  }

  @Override
  protected void after() {
    pg.stop();
    super.after();
  }
}
