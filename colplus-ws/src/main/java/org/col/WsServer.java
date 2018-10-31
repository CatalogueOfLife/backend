package org.col;

import io.dropwizard.setup.Environment;
import org.col.dw.PgApp;
import org.col.dw.es.ManagedEsClient;
import org.col.es.EsClientFactory;
import org.col.es.NameUsageSearchService;
import org.col.resources.*;
import org.elasticsearch.client.RestClient;

public class WsServer extends PgApp<WsServerConfig> {
  
  private Object adminApi;

  
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
    
    RestClient esClient = new EsClientFactory(cfg.es).createClient();
    env.lifecycle().manage(new ManagedEsClient(esClient));
    
    NameUsageSearchService svc = new NameUsageSearchService(esClient, cfg.es);
    env.jersey().register(new NameResource(svc));   

    env.jersey().register(new ColSourceResource());
    env.jersey().register(new DecisionResource());
    env.jersey().register(new DocsResource(cfg));
    env.jersey().register(new DataPackageResource());
    env.jersey().register(new DatasetResource(getSqlSessionFactory()));
    env.jersey().register(new ReferenceResource());
    env.jersey().register(new SectorResource());
    env.jersey().register(new TaxonResource());
    env.jersey().register(new TreeResource());
    env.jersey().register(new ParserResource());
    env.jersey().register(new VerbatimResource());
    env.jersey().register(new VocabResource());
    
    env.jersey().register(new MatchingForwardingResource(cfg.adminApi));
  }

}
