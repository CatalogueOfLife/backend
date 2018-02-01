package org.col.dw;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.dropwizard.Application;
import io.dropwizard.forms.MultiPartBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.dw.api.jackson.ApiModule;
import org.col.dw.cors.CorsBundle;
import org.col.dw.db.MybatisBundle;
import org.col.dw.jersey.JerseyProviderBundle;

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
    bootstrap.getObjectMapper().registerModule(new ApiModule());
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
		// JSON defaults
		env.getObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
		env.getObjectMapper().registerModule(new JavaTimeModule());
		env.getObjectMapper().configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    env.getObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

}
