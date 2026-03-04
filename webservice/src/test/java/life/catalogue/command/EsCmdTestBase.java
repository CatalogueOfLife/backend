package life.catalogue.command;

import life.catalogue.WsServer;
import life.catalogue.WsServerConfig;
import life.catalogue.common.io.TempFile;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.common.util.YamlUtils;
import life.catalogue.config.EsConfig;
import life.catalogue.db.PgDbConfig;
import life.catalogue.es.EsSetupRule;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

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

import io.dropwizard.core.cli.Cli;
import io.dropwizard.core.cli.Command;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.util.JarLocation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
public abstract class EsCmdTestBase extends CmdTestBase {
  private static final Logger LOG = LoggerFactory.getLogger(EsCmdTestBase.class);

  @ClassRule
  public static EsSetupRule esRule = new EsSetupRule(2);


  public EsCmdTestBase(Supplier<Command> cmdSupply) {
    super(cmdSupply);
  }

  public EsCmdTestBase(Supplier<Command> cmdSupply, TestDataRule rule) {
    super(cmdSupply, rule);
  }

  @Override
  protected EsConfig configureES() {
    return esRule.getEsConfig();
  }
}
