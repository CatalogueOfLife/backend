package org.col;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.col.commands.hello.HelloCmd;
import org.col.commands.initdb.InitDbCmd;
import org.col.config.ColAppConfig;
import org.col.db.MybatisBundle;
import org.col.jersey.JerseyProviderBundle;
import org.col.resources.NameResource;

public class ColApp extends Application<ColAppConfig> {

  public static void main(final String[] args) throws Exception {
    new ColApp().run(args);
  }

  @Override
  public String getName() {
    return "Catalogue of Life";
  }

  @Override
  public void initialize(final Bootstrap<ColAppConfig> bootstrap) {
    // our mybatis classes
    bootstrap.addBundle(new MybatisBundle());
    // various custom jersey providers
    bootstrap.addBundle(new JerseyProviderBundle());
    // commands
    bootstrap.addCommand(new HelloCmd());
    bootstrap.addCommand(new InitDbCmd());
  }

  @Override
  public void run(final ColAppConfig config, final Environment environment) {
    // health

    // JSON defaults
    environment.getObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    //environment.getObjectMapper().configure(SerializationFeature.WRITE_NULL_MAP_VALUES, true);

    // resources
    environment.jersey().register(new NameResource());
  }

}
