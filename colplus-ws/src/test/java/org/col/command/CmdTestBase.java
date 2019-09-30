package org.col.command;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;

import com.codahale.metrics.MetricRegistry;
import com.google.common.io.Resources;
import io.dropwizard.cli.Cli;
import io.dropwizard.cli.Command;
import io.dropwizard.logging.LoggingFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.util.JarLocation;
import org.col.WsServer;
import org.col.WsServerConfig;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
public abstract class CmdTestBase {
  private static final Logger LOG = LoggerFactory.getLogger(CmdTestBase.class);
  protected final File cfg;
  private final Command cmd;
  
  private Cli cli;
  
  public CmdTestBase(Command cmd) {
    this.cmd = cmd;
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
    bootstrap.addCommand(cmd);
    
    // Build what'll run the command and interpret arguments
    cli = new Cli(location, bootstrap, System.out, System.err);
  }
  
  /**
   * Only here to avoid meaningful serialization of the logging settings.
   * Since DW 1.2 the logging factory is created lazily and cannot be set to null.
   */
  public static class DevNullLoggingFactory implements LoggingFactory {
    @Override
    public void configure(MetricRegistry metricRegistry, String name) {
    }
    
    @Override
    public void stop() {
    }
    
    @Override
    public void reset() {
    }
  }
  
  /**
   * Executes the cli with the given arguments, adding a final argument to the test config file.
   */
  public boolean run(String... args) throws Exception {
    // now run the real arg
    final int N = args.length;
    args = Arrays.copyOf(args, N + 1);
    args[N] = cfg.getAbsolutePath();
    
    // make sure the cli run fine
    return cli.run(args);
  }
}
