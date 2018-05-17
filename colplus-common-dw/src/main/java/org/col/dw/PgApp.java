package org.col.dw;

import io.dropwizard.Application;
import io.dropwizard.forms.MultiPartBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.jackson.ApiModule;
import org.col.dw.cors.CorsBundle;
import org.col.dw.db.MybatisBundle;
import org.col.dw.jersey.provider.JerseyProviderBundle;

public abstract class PgApp<T extends PgAppConfig> extends Application<T> {

  private final MybatisBundle mybatis = new MybatisBundle();

  @Override
	public void initialize(final Bootstrap<T> bootstrap) {
		// our mybatis classes
		bootstrap.addBundle(mybatis);
		// various custom jersey providers
		bootstrap.addBundle(new JerseyProviderBundle());
    bootstrap.addBundle(new MultiPartBundle());
    bootstrap.addBundle(new CorsBundle());
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

  @Override
  public void run(T cfg, Environment env) {
	}

}
