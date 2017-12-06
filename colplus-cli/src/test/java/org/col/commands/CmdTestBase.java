package org.col.commands;

import io.dropwizard.cli.Cli;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.util.JarLocation;
import org.col.commands.config.CliConfig;
import org.col.commands.initdb.InitDbCmd;
import org.col.db.mapper.PgSetupRule;
import org.col.util.YamlUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
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
  private final PrintStream originalOut = System.out;
  private final PrintStream originalErr = System.err;
  private final InputStream originalIn = System.in;
  private final ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
  private final ByteArrayOutputStream stdErr = new ByteArrayOutputStream();
  protected Cli cli;
  private final CliConfig cfg;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  private File tempDbCfg;

  public CmdTestBase() {
    this.cfg = new CliConfig();
  }

  public CmdTestBase(CliConfig cfg) {
    this.cfg = cfg;
  }

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
    final Bootstrap<CliConfig> bootstrap = new Bootstrap<>(new CliApp());
    bootstrap.addCommand(new InitDbCmd());

    // Redirect stdout and stderr to our byte streams
    System.setOut(new PrintStream(stdOut));
    System.setErr(new PrintStream(stdErr));

    // Build what'll run the command and interpret arguments
    cli = new Cli(location, bootstrap, stdOut, stdErr);
  }

  @After
  public void teardown() {
    System.setOut(originalOut);
    System.setErr(originalErr);
    System.setIn(originalIn);
    tempDbCfg.delete();
  }

  /**
   * Executes the cli with the given arguments, adding a final argument to the test config file.
   */
  public void run(String ... args) throws Exception {
    final int N = args.length;
    args = Arrays.copyOf(args, N + 1);
    args[N] = tempDbCfg.getAbsolutePath();

    assertTrue(cli.run(args));
  }
}
