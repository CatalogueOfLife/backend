package org.col.dw;

import de.lhorn.dropwizard.dashboard.Dashboard;
import io.dropwizard.Application;
import io.dropwizard.forms.MultiPartBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.jackson.ApiModule;
import org.col.dw.auth.AuthBundle;
import org.col.dw.auth.JwtCoder;
import org.col.dw.cors.CorsBundle;
import org.col.dw.db.MybatisBundle;
import org.col.dw.health.NameParserHealthCheck;
import org.col.dw.jersey.provider.JerseyProviderBundle;
import org.col.parser.NameParser;

public abstract class PgApp<T extends PgAppConfig> extends Application<T> {

  private final MybatisBundle mybatis = new MybatisBundle();
  private final AuthBundle auth = new AuthBundle();

  @Override
	public void initialize(final Bootstrap<T> bootstrap) {
		// our mybatis classes
		bootstrap.addBundle(mybatis);
		// various custom jersey providers
		bootstrap.addBundle(new JerseyProviderBundle());
    bootstrap.addBundle(new MultiPartBundle());
    bootstrap.addBundle(new CorsBundle());
    // authentication which requires the UserMapper from mybatis AFTER the mybatis bundle has run
    bootstrap.addBundle(auth);
    // customize jackson
    ApiModule.configureMapper(bootstrap.getObjectMapper());
  }

  /**
   * Make sure to call this after the app has been bootstrapped, otherwise its null.
   * Methods to add tasks or healthchecks can be sure to use the session factory.
   */
  public SqlSessionFactory getSqlSessionFactory() {
    return mybatis.getSqlSessionFactory();
  }
  
  public JwtCoder getJwtCoder() {
    return auth.getJwtCoder();
  }
  
  @Override
  public void run(T cfg, Environment env) {
    // finally provide the SqlSessionFactory
    auth.getIdentityService().setSqlSessionFactory(mybatis.getSqlSessionFactory());
    // name parser
    NameParser.PARSER.register(env.metrics());
    env.healthChecks().register("name-parser", new NameParserHealthCheck());

    final Dashboard dashboard = new Dashboard(env, cfg.getDashboardConfiguration());
	}
	
 
}
