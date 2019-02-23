package org.col;

import io.dropwizard.setup.Environment;
import org.col.common.io.DownloadUtil;
import org.col.dw.PgApp;
import org.col.dw.es.ManagedEsClient;
import org.col.es.EsClientFactory;
import org.col.es.NameUsageSearchService;
import org.col.img.ImageService;
import org.col.resources.*;
import org.elasticsearch.client.RestClient;

import static org.col.es.EsConfig.ES_INDEX_NAME_USAGE;

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
    
    final RestClient esClient = new EsClientFactory(cfg.es).createClient();
    env.lifecycle().manage(new ManagedEsClient(esClient));
    NameUsageSearchService nuss = new NameUsageSearchService(cfg.es.indexName(ES_INDEX_NAME_USAGE), esClient);
    final ImageService imgService = new ImageService(cfg.img);
    env.jersey().register(new DataPackageResource());
    env.jersey().register(new DatasetResource(getSqlSessionFactory(), imgService, cfg::scratchDir, new DownloadUtil(super.httpClient)));
    env.jersey().register(new DecisionResource());
    env.jersey().register(new DocsResource(cfg));
    env.jersey().register(new NameResource(nuss));
    env.jersey().register(new NameSearchResource(nuss));
    env.jersey().register(new ParserResource());
    env.jersey().register(new ReferenceResource());
    env.jersey().register(new SectorResource());
    env.jersey().register(new TaxonResource());
    env.jersey().register(new TreeResource());
    env.jersey().register(new UserResource(getJwtCoder()));
    env.jersey().register(new VerbatimResource());
    env.jersey().register(new VocabResource());
  }
  
}
