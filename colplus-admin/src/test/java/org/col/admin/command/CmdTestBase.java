package org.col.admin.command;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
public abstract class CmdTestBase {
  private static final Logger LOG = LoggerFactory.getLogger(CmdTestBase.class);
  protected final AdminServerConfig cfg;
  private final Command cmd;
  
  private Cli cli;
  protected File tempDbCfg;
  
  public CmdTestBase(Command cmd) {
    this.cmd = cmd;
    this.cfg = new AdminServerConfig();
  }
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule(true);
  
  @Before
  public void setUp() throws Exception {
    // swap out db configs for the ones used by the PgSetupRule
    tempDbCfg = Files.createTempFile("colplus-db", ".yaml").toFile();
    cfg.db = PgSetupRule.getCfg();
    cfg.adminDb.user = cfg.db.user;
    cfg.adminDb.password = cfg.db.password;
    cfg.img.repo = Paths.get("/tmp/imgrepo");
    
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
    
    // Add the command we want to test
    final Bootstrap<AdminServerConfig> bootstrap = new Bootstrap<>(new AdminServer());
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
  
  @After
  public void teardown() {
    LOG.debug("Remove tmp db configs at {}", tempDbCfg.getAbsolutePath());
    tempDbCfg.delete();
  }
  
  /**
   * Executes the cli with the given arguments, adding a final argument to the test config file.
   */
  public boolean run(String... args) throws Exception {
    return run(false, args);
  }
  
  /**
   * Executes the cli with the given arguments, adding a final argument to the test config file.
   */
  public boolean run(boolean initdb, String... args) throws Exception {
    // first run initdb?
    if (initdb) {
      InitDbCmd.execute(cfg);
    }
    
    // now run the real arg
    final int N = args.length;
    args = Arrays.copyOf(args, N + 1);
    args[N] = tempDbCfg.getAbsolutePath();
    
    // make sure the cli run fine
    return cli.run(args);
  }
}
