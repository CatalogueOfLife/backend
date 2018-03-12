package org.col;

import io.dropwizard.setup.Environment;
import org.col.dw.PgApp;
import org.col.resources.*;

public class WsServer extends PgApp<WsServerConfig> {

  public static void main(final String[] args) throws Exception {
		new WsServer().run(args);
	}

	@Override
	public String getName() {
		return "ws-server";
	}

  @Override
  public void run(WsServerConfig cfg, Environment env) {
    super.run(cfg, env);

    env.jersey().register(new DocsResource(cfg));
    env.jersey().register(new DatasetResource());
    env.jersey().register(new ReferenceResource());
    env.jersey().register(new NameResource());
    env.jersey().register(new TaxonResource());
    env.jersey().register(new ParserResource());
    env.jersey().register(new VocabResource());
  }

}
