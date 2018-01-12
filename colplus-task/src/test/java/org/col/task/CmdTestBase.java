package org.col.task;

import io.dropwizard.cli.Cli;
import io.dropwizard.cli.Command;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.util.JarLocation;
import org.col.task.common.TaskServerConfig;
import org.col.command.initdb.InitDbCmd;
import org.col.db.mapper.PgSetupRule;
import org.col.util.YamlUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
public abstract class CmdTestBase {
  protected Cli cli;
  private final TaskServerConfig cfg;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  private File tempDbCfg;

  public CmdTestBase() {
    this.cfg = new TaskServerConfig();
  }

  public CmdTestBase(TaskServerConfig cfg) {
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
    cfg.setLoggingFactory(null);
    cfg.setServerFactory(null);
    cfg.setMetricsFactory(null);
    YamlUtils.write(cfg, tempDbCfg);


    // Setup necessary mock
    final JarLocation location = mock(JarLocation.class);
    when(location.getVersion()).thenReturn(Optional.of("1.0-SNAPSHOT"));

    // Add commands you want to test
    final Bootstrap<TaskServerConfig> bootstrap = new Bootstrap<>(new TaskServer());
    bootstrap.addCommand(new InitDbCmd());
    Command cmd = registerCommand();
    if (cmd != null) {
      bootstrap.addCommand(cmd);
    }

    // Build what'll run the command and interpret arguments
    cli = new Cli(location, bootstrap, System.out, System.err);
  }

  @After
  public void teardown() {
    tempDbCfg.delete();
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
