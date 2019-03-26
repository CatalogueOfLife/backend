package org.col;

import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.col.api.vocab.Datasets;
import org.col.assembly.AssemblyCoordinator;
import org.col.command.es.IndexAllCmd;
import org.col.command.export.AcExporter;
import org.col.command.export.ExportCmd;
import org.col.command.initdb.InitDbCmd;
import org.col.command.neoshell.ShellCmd;
import org.col.common.io.DownloadUtil;
import org.col.config.WsServerConfig;
import org.col.dw.PgApp;
import org.col.dw.es.ManagedEsClient;
import org.col.es.EsClientFactory;
import org.col.es.NameUsageIndexService;
import org.col.es.NameUsageIndexServiceEs;
import org.col.es.NameUsageSearchService;
import org.col.gbifsync.GbifSync;
import org.col.img.ImageService;
import org.col.importer.ContinuousImporter;
import org.col.importer.ImportManager;
import org.col.matching.NameIndex;
import org.col.matching.NameIndexFactory;
import org.col.resources.*;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import static org.col.es.EsConfig.ES_INDEX_NAME_USAGE;

public class WsServer extends PgApp<WsServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(WsServer.class);
  // milliseconds to wait during shutdown before forcing a shutdown
  public static final int MILLIS_TO_DIE = 12000;
  
  private Object adminApi;
  
  public static void main(final String[] args) throws Exception {
    SLF4JBridgeHandler.install();
    new WsServer().run(args);
  }
  
  @Override
  public void initialize(Bootstrap<WsServerConfig> bootstrap) {
    super.initialize(bootstrap);
    
    // add some cli commands not accessible via the admin interface
    bootstrap.addCommand(new InitDbCmd());
    bootstrap.addCommand(new ShellCmd());
    bootstrap.addCommand(new IndexAllCmd());
    bootstrap.addCommand(new ExportCmd());
  }
  
  @Override
  public String getName() {
    return "ws-server";
  }
  
  @Override
  public void run(WsServerConfig cfg, Environment env) {
    super.run(cfg, env);
  
    // turn off user cache as longs as we cannot sync between JVMs
    cfg.authCache = false;
  
  
    // ES
    final RestClient esClient = new EsClientFactory(cfg.es).createClient();

    NameUsageIndexService indexService;
    if (esClient == null) {
      LOG.warn("No Elastic Search configured, use pass through indexing");
      indexService = NameUsageIndexService.passThru();
    } else {
      env.lifecycle().manage(new ManagedEsClient(esClient));
      indexService = new NameUsageIndexServiceEs(esClient, cfg.es, getSqlSessionFactory());
    }
    NameUsageSearchService nuss = new NameUsageSearchService(cfg.es.indexName(ES_INDEX_NAME_USAGE), esClient);
    
    // images
    final ImageService imgService = new ImageService(cfg.img);
  
    // name index
    NameIndex ni;
    if (cfg.namesIndexFile == null) {
      LOG.info("Using volatile in memory names index");
      ni = NameIndexFactory.memory(Datasets.PCAT, getSqlSessionFactory());
    } else {
      LOG.info("Using names index at {}", cfg.namesIndexFile.getAbsolutePath());
      ni = NameIndexFactory.persistent(Datasets.PCAT, cfg.namesIndexFile, getSqlSessionFactory());
    }
  
    // async importer
    final ImportManager importManager = new ImportManager(cfg, env.metrics(), super.httpClient, getSqlSessionFactory(), ni, indexService, imgService);
    env.lifecycle().manage(importManager);
    env.jersey().register(new ImporterResource(importManager, getSqlSessionFactory()));
  
    if (cfg.importer.continousImportPolling > 0) {
      LOG.info("Enable continuous importing");
      env.lifecycle().manage(new ContinuousImporter(cfg.importer, importManager, getSqlSessionFactory()));
    } else {
      LOG.warn("Disable continuous importing");
    }
  
    // activate gbif sync?
    if (cfg.gbif.syncFrequency > 0) {
      LOG.info("Enable GBIF dataset sync");
      env.lifecycle().manage(new GbifSync(cfg.gbif, getSqlSessionFactory(), jerseyRxClient));
    } else {
      LOG.warn("Disable GBIF dataset sync");
    }
  
    // exporter
    AcExporter exporter = new AcExporter(cfg);
    // assembly
    AssemblyCoordinator assembly = new AssemblyCoordinator(getSqlSessionFactory(), env.metrics());
    env.lifecycle().manage(assembly);
  
    
    // resources
    env.jersey().register(new DataPackageResource());
    env.jersey().register(new DatasetResource(getSqlSessionFactory(), imgService, cfg::scratchDir, new DownloadUtil(super.httpClient)));
    env.jersey().register(new DecisionResource(getSqlSessionFactory(), indexService));
    env.jersey().register(new DocsResource(cfg));
    env.jersey().register(new NameResource(nuss));
    env.jersey().register(new NameSearchResource(nuss));
    env.jersey().register(new ParserResource());
    env.jersey().register(new ReferenceResource());
    env.jersey().register(new SectorResource(getSqlSessionFactory()));
    env.jersey().register(new TaxonResource());
    env.jersey().register(new TreeResource());
    env.jersey().register(new UserResource(getJwtCoder()));
    env.jersey().register(new VerbatimResource());
    env.jersey().register(new VocabResource());
    env.jersey().register(new MatchingResource(ni));
    env.jersey().register(new AssemblyResource(assembly, getSqlSessionFactory(), exporter));
    env.jersey().register(new AdminResource(getSqlSessionFactory(), new DownloadUtil(super.httpClient), cfg.normalizer, imgService));
  
  }
  
}
