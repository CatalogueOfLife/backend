package org.col;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.google.common.collect.Lists;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.common.io.PortUtil;
import org.col.common.util.YamlUtils;
import org.col.db.EmbeddedColPg;
import org.col.db.PgConfig;
import org.col.db.PgSetupRule;
import org.col.dw.BasicAuthClientFilter;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An adaptation of the generic DropwizardAppRule that can be used as a junit class rule
 * to create integration tests against a running dropwizard instance.
 * <p>
 * WsServerRule spins up an embedded postgres server
 * and updates the PgConfig with the matching config parameters to access it via the MyBatisModule.
 * <p>
 * It also selects and configures DW to use a free application port.
 *
 * The created jersey client includes basic auth which needs to be set for each call like this:
 *
 * <code>
 *      Response response = client.target("http://localhost:8080/rest/homer/contact").request()
 *         .property(HTTP_AUTHENTICATION_BASIC_USERNAME, "homer")
 *         .property(HTTP_AUTHENTICATION_BASIC_PASSWORD, "p1swd745").get();
 * </code>
 */
public class WsServerRule extends DropwizardAppRule<WsServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(WsServerRule.class);
  private static EmbeddedColPg pg;
  
  public WsServerRule(String configPath, ConfigOverride... configOverrides) {
    super(WsServer.class, configPath, setupPg(configPath, configOverrides));
  }
  
  static class PgConfigInApp {
    public PgConfig db = new PgConfig();
  }

  static ConfigOverride[] setupPg(String configPath, ConfigOverride... configOverrides) {
    List<ConfigOverride> overrides = Lists.newArrayList(configOverrides);
    try {
      PgConfigInApp cfg = YamlUtils.read(PgConfigInApp.class, new File(configPath));
      if (cfg.db.embedded()) {
        pg = new EmbeddedColPg(cfg.db);
        pg.start();
      } else {
        LOG.info("Use external Postgres server {}/{}", cfg.db.host, cfg.db.database);
      }
  
      PgSetupRule.initDb(cfg.db);

      overrides.add(ConfigOverride.config("db.host", cfg.db.host));
      overrides.add(ConfigOverride.config("db.port", String.valueOf(cfg.db.port)));
      overrides.add(ConfigOverride.config("db.database", cfg.db.database));
      overrides.add(ConfigOverride.config("db.user", cfg.db.user));
      overrides.add(ConfigOverride.config("db.password", cfg.db.password));

    } catch (Exception e) {
      throw new RuntimeException("Failed to read postgres configuration from " + configPath, e);
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
  
  public SqlSessionFactory getSqlSessionFactory() {
    return ((WsServer) getTestSupport().getApplication()).getSqlSessionFactory();
  }
  
  @Override
  protected void after() {
    if (pg != null) {
      pg.stop();
    }
    super.after();
  }
  
  @Override
  protected JerseyClientBuilder clientBuilder() {
    JerseyClientBuilder builder = super.clientBuilder();
    BasicAuthClientFilter basicAuthFilter = new BasicAuthClientFilter();
    builder.register(basicAuthFilter);
    return builder;
  }
  
}
