package org.col.admin;

import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.col.admin.command.export.AcExporter;
import org.col.admin.assembly.AssemblyCoordinator;
import org.col.admin.command.es.IndexAllCmd;
import org.col.admin.command.export.ExportCmd;
import org.col.admin.command.initdb.InitDbCmd;
import org.col.admin.command.neoshell.ShellCmd;
import org.col.admin.config.AdminServerConfig;
import org.col.admin.gbifsync.GbifSync;
import org.col.admin.importer.ContinuousImporter;
import org.col.admin.importer.ImportManager;
import org.col.admin.matching.NameIndex;
import org.col.admin.matching.NameIndexFactory;
import org.col.admin.resources.AdminResource;
import org.col.admin.resources.AssemblyResource;
import org.col.admin.resources.ImporterResource;
import org.col.admin.resources.MatchingResource;
import org.col.api.vocab.Datasets;
import org.col.common.io.DownloadUtil;
import org.col.dw.PgApp;
import org.col.dw.es.ManagedEsClient;
import org.col.es.EsClientFactory;
import org.col.img.ImageService;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class AdminServer extends PgApp<AdminServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(AdminServer.class);
  // milliseconds to wait during shutdown before forcing a shutdown
  public static final int MILLIS_TO_DIE = 12000;
  
  public static void main(final String[] args) throws Exception {
    SLF4JBridgeHandler.install();
    new AdminServer().run(args);
  }
  
  @Override
  public String getName() {
    return "admin-server";
  }
  
  @Override
  public void initialize(Bootstrap<AdminServerConfig> bootstrap) {
    super.initialize(bootstrap);
    
    // add some cli commands not accessible via the admin interface
    bootstrap.addCommand(new InitDbCmd());
    bootstrap.addCommand(new ShellCmd());
    bootstrap.addCommand(new IndexAllCmd());
    bootstrap.addCommand(new ExportCmd());
  }
  
  @Override
  public void run(AdminServerConfig cfg, Environment env) {
    super.run(cfg, env);
  
    // turn off user cache as longs as we cannot sync between JVMs
    cfg.authCache = false;
    
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
    env.jersey().register(new MatchingResource(ni));
    
    RestClient esClient = null;
    if (cfg.es != null) {
      esClient = new EsClientFactory(cfg.es).createClient();
      env.lifecycle().manage(new ManagedEsClient(esClient));
    }
    
    // setup async importer
    final ImportManager importManager = new ImportManager(cfg, env.metrics(), super.httpClient, getSqlSessionFactory(), ni, esClient, imgService);
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
    
    // admin resource
    env.jersey().register(new AdminResource(getSqlSessionFactory(), new DownloadUtil(super.httpClient), cfg.normalizer, imgService));
  
    // exporter
    AcExporter exporter = new AcExporter(cfg);
    // assembly
    AssemblyCoordinator assembly = new AssemblyCoordinator(getSqlSessionFactory(), env.metrics());
    env.lifecycle().manage(assembly);
    env.jersey().register(new AssemblyResource(assembly, getSqlSessionFactory(), exporter));
    
  }
  
  
}
