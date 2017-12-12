package org.col;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.dropwizard.Application;
import io.dropwizard.forms.MultiPartBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.col.api.jackson.ApiModule;
import org.col.db.MybatisBundle;
import org.col.jersey.JerseyProviderBundle;
import org.col.resources.*;

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
    bootstrap.addBundle(new MultiPartBundle());
    // customize jackson
    bootstrap.getObjectMapper().registerModule(new ApiModule());
  }

	@Override
	public void run(final ColAppConfig config, final Environment environment) {
		// health

		// JSON defaults
		environment.getObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
		environment.getObjectMapper().registerModule(new JavaTimeModule());
		environment.getObjectMapper().configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

		// resources
		environment.jersey().register(new DatasetResource());
		environment.jersey().register(new ReferenceResource());
		environment.jersey().register(new NameResource());
		environment.jersey().register(new TaxonResource());
    environment.jersey().register(new ParserResource());

	}

}
