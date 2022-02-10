package life.catalogue.command;

import life.catalogue.WsServer;
import life.catalogue.WsServerConfig;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Resources;

import io.dropwizard.cli.Cli;
import io.dropwizard.cli.Command;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.util.JarLocation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
public abstract class CmdTestBase {
  private static final Logger LOG = LoggerFactory.getLogger(CmdTestBase.class);
  protected final File cfg;
  private final Supplier<Command> cmdSupply;

  private Cli cli;
  
  public CmdTestBase(Supplier<Command> cmdSupply) {
    this.cmdSupply = cmdSupply;
    try {
      URL res = Resources.getResource("config-test.yaml");
      cfg = Paths.get(res.toURI()).toFile();
      LOG.info("Use config file at {}", cfg.getAbsolutePath());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  @Before
  public void setUp() throws Exception {
    // Setup necessary mock
    final JarLocation location = mock(JarLocation.class);
    when(location.getVersion()).thenReturn(Optional.of("1.0-SNAPSHOT"));
    
    // Add the command we want to test
    final Bootstrap<WsServerConfig> bootstrap = new Bootstrap<>(new WsServer());
    bootstrap.addCommand(cmdSupply.get());
    
    // Build what'll run the command and interpret arguments
    cli = new Cli(location, bootstrap, System.out, System.err);
  }

  /**
   * Executes the cli with the given arguments, adding a final argument to the test config file.
   */
  public Optional<Throwable> run(String... args) throws Exception {
    // now run the real arg
    final int N = args.length;
    args = Arrays.copyOf(args, N + 1);
    args[N] = cfg.getAbsolutePath();

    //System.exit(1);
    // make sure the cli run fine
    return cli.run(args);
  }
}
