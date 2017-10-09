package org.col.commands.importer;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.col.commands.config.CliConfig;
import org.col.commands.importer.neo.NeoDbFactory;
import org.col.commands.importer.neo.NormalizerStore;
import org.col.db.mapper.DatasetMetricsMapper;
import org.gbif.util.DownloadUtil;

import java.io.File;
import java.net.URL;

public class ImporterCmd extends ConfiguredCommand<CliConfig> {

  public ImporterCmd() {
    super("importer", "Imports the latest version of an external dataset into staging");
  }

  @Override
  protected void run(Bootstrap<CliConfig> bootstrap, Namespace namespace, CliConfig cfg) throws Exception {
    final int datasetKey = 1234;

    File dwca = cfg.normalizer.source(datasetKey);
    URL dwcaUrl = null;
    System.out.format("Downloading %s from %s!\n", datasetKey, dwca);
    DownloadUtil.download(dwcaUrl, dwca);

    System.out.format("Normalizing %s!\n", datasetKey);
    NormalizerStore store = NeoDbFactory.create(cfg.normalizer, datasetKey);
    Normalizer normalizer = new Normalizer(store, dwca);
    normalizer.run();

    System.out.format("Importing %s into Postgres!\n", datasetKey);
    Importer importer = null;//Importer.create(cfg, datasetKey, nameUsageService, usageService, sqlService);
    importer.run();

    System.out.format("Analyzing %s!\n", datasetKey);
    DatasetMetricsMapper datasetMetricsMapper = null;
    Analyser analyser = Analyser.create(datasetKey, datasetMetricsMapper);
    analyser.run();

    System.out.format("Import %s completed!\n", datasetKey);
  }
}
