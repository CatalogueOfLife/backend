package org.col.commands;

import io.dropwizard.cli.Cli;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.util.JarLocation;
import org.col.commands.config.CliConfig;
import org.col.commands.initdb.InitDbCmd;
import org.col.db.mapper.PgSetupRule;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
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

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Before
  public void setUp() throws Exception {
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
  }

  /**
   * Executes the cli with the given arguments, adding a final argument to the test config file.
   */
  public void run(String ... args) throws Exception {
    final int N = args.length;
    args = Arrays.copyOf(args, N + 1);
    args[N] = "target/test-classes/cli-test.yaml";

    assertTrue(cli.run(args));
  }
}
