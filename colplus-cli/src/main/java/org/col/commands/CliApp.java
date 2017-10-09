package org.col.commands;

import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.col.commands.config.CliConfig;
import org.col.commands.hello.HelloCmd;
import org.col.commands.initdb.InitDbCmd;
import org.col.db.MybatisBundle;

public class CliApp extends Application<CliConfig> {

  public static void main(final String[] args) throws Exception {
    new CliApp().run(args);
  }

  @Override
  public String getName() {
    return "Catalogue of Life";
  }

  @Override
  public void initialize(final Bootstrap<CliConfig> bootstrap) {
    // our mybatis classes
    bootstrap.addBundle(new MybatisBundle());
    // commands
    bootstrap.addCommand(new HelloCmd());
    bootstrap.addCommand(new InitDbCmd());
  }

  @Override
  public void run(final CliConfig config, final Environment environment) {
  }

}
