package org.col.commands.importer;

import com.zaxxer.hikari.HikariDataSource;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.col.commands.config.CliConfig;
import org.col.commands.importer.dwca.Normalizer;
import org.col.commands.importer.neo.NeoDbFactory;
import org.col.commands.importer.neo.NormalizerStore;
import org.col.db.MybatisFactory;
import org.col.db.mapper.DatasetMetricsMapper;
import org.gbif.util.DownloadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;

public class ImporterCmd extends ConfiguredCommand<CliConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(ImporterCmd.class);

  public ImporterCmd() {
    super("importer", "Imports the latest version of an external dataset into staging");
  }


  @Override
  protected void run(Bootstrap<CliConfig> bootstrap, Namespace namespace, CliConfig cfg) throws Exception {
    final int datasetKey = 1234;

    // create datasource
    HikariDataSource ds = cfg.db.pool();
    try {
      //TODO: enable imports in different formats!!!
      File dwca = cfg.normalizer.source(datasetKey);
      URL dwcaUrl = null;
      LOG.info("Downloading {} from {}!", datasetKey, dwca);
      DownloadUtil.download(dwcaUrl, dwca);

      LOG.info("Normalizing {}!", datasetKey);
      NormalizerStore store = NeoDbFactory.create(cfg.normalizer, datasetKey);
      Normalizer normalizer = new Normalizer(store, dwca);
      normalizer.run();

      LOG.info("Importing {} into Postgres!", datasetKey);
      Importer importer = new Importer(datasetKey,
          NeoDbFactory.open(cfg.normalizer, datasetKey),
          MybatisFactory.configure(ds, "importer"),
          cfg.importer
      );
      importer.run();

      LOG.info("Analyzing {}!", datasetKey);
      DatasetMetricsMapper datasetMetricsMapper = null;
      Analyser analyser = Analyser.create(datasetKey, datasetMetricsMapper);
      analyser.run();

      LOG.info("Import {} completed!", datasetKey);

    } finally {
      ds.close();
    }
  }
}
