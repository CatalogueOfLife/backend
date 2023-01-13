package life.catalogue.command;

import life.catalogue.WsServer;
import life.catalogue.WsServerConfig;
import life.catalogue.common.io.TempFile;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.common.util.YamlUtils;
import life.catalogue.db.PgDbConfig;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
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
  protected final TempFile cfg;
  private final Supplier<Command> cmdSupply;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.empty();

  private Cli cli;
  
  public CmdTestBase(Supplier<Command> cmdSupply) {
    this.cmdSupply = cmdSupply;
    cfg = new TempFile("col-cfg", ".yaml");
    // prepare config file
    try {
      URL res = Resources.getResource("config-test.yaml");
      try (var w = UTF8IoUtils.writerFromFile(cfg.file)) {
        w.write(Resources.toString(res, Charsets.UTF_8));
        // append db & adminDb cfg for pg container
        var db = PgSetupRule.getCfg();
        w.write("\ndb:\n");
        YamlUtils.write(db, 2, w);

        w.write("\nadminDb:\n");
        PgDbConfig adb = new PgDbConfig();
        adb.database = PgSetupRule.ADMIN_DB_NAME;
        adb.password = db.password;
        adb.user = db.user;
        YamlUtils.write(adb, 2, w);
      }
      LOG.info("Wrote cli config file to {}", cfg.file.getAbsolutePath());
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

  @After
  public void tearDown() throws Exception {
    cfg.close();
  }

  /**
   * Executes the cli with the given arguments, adding a final argument to the test config file.
   */
  public Optional<Throwable> run(String... args) throws Exception {
    // now run the real arg
    final int N = args.length;
    args = Arrays.copyOf(args, N + 1);
    args[N] = cfg.file.getAbsolutePath();

    //System.exit(1);
    // make sure the cli run fine
    return cli.run(args);
  }
}
