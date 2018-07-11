package org.col.admin.command;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Optional;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.cli.Cli;
import io.dropwizard.cli.Command;
import io.dropwizard.logging.LoggingFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.util.JarLocation;
import org.col.admin.AdminServer;
import org.col.admin.command.initdb.InitDbCmd;
import org.col.admin.config.AdminServerConfig;
import org.col.common.util.YamlUtils;
import org.col.db.PgSetupRule;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
public abstract class CmdTestBase {
  protected Cli cli;
  private final AdminServerConfig cfg;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  private File tempDbCfg;

  public CmdTestBase() {
    this.cfg = new AdminServerConfig();
  }

  public CmdTestBase(AdminServerConfig cfg) {
    this.cfg = cfg;
  }

  public abstract Command registerCommand();

  @Before
  public void setUp() throws Exception {
    // swap out db configs for the ones used by the PgSetupRule
    tempDbCfg = Files.createTempFile("colplus-db", ".yaml").toFile();
    cfg.db = PgSetupRule.getCfg();
    // somehow serde doesnt work with the inherited Configuration props, set them to null to ignore them
    cfg.client = null;
    cfg.setServerFactory(null);
    cfg.setMetricsFactory(null);
    // Since DW 1.2 the logging factory is created lazily and cannot be set to null.
    cfg.setLoggingFactory(new DevNullLoggingFactory());
    YamlUtils.write(cfg, tempDbCfg);

    // Setup necessary mock
    final JarLocation location = mock(JarLocation.class);
    when(location.getVersion()).thenReturn(Optional.of("1.0-SNAPSHOT"));

    // Add commands you want to test
    final Bootstrap<AdminServerConfig> bootstrap = new Bootstrap<>(new AdminServer());
    bootstrap.addCommand(new InitDbCmd());
    Command cmd = registerCommand();
    if (cmd != null) {
      bootstrap.addCommand(cmd);
    }

    // Build what'll run the command and interpret arguments
    cli = new Cli(location, bootstrap, System.out, System.err);
  }

  /**
   * Only here to avoid meaningful serialization of the logging settings.
   * Since DW 1.2 the logging factory is created lazily and cannot be set to null.
   */
  public static class DevNullLoggingFactory implements LoggingFactory {
    @Override
    public void configure(MetricRegistry metricRegistry, String name) { }
    @Override
    public void stop() {}
    @Override
    public void reset() { }
  }

  @After
  public void teardown() {
    System.out.println(tempDbCfg.getAbsolutePath());
    //tempDbCfg.delete();
  }

  /**
   * Executes the cli with the given arguments, adding a final argument to the test config file.
   */
  public void run(boolean initdb, String ... args) throws Exception {
    // first run initdb?
    if (initdb) {
      cli.run("initdb", "--prompt", "0", tempDbCfg.getAbsolutePath());
    }

    // now run the real arg
    final int N = args.length;
    args = Arrays.copyOf(args, N + 1);
    args[N] = tempDbCfg.getAbsolutePath();

    assertTrue(cli.run(args));
  }
}
