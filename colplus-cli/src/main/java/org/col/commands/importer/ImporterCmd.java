package org.col.commands.importer;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.col.commands.config.CliConfig;
import org.col.db.mapper.DatasetMetricsMapper;

public class ImporterCmd extends ConfiguredCommand<CliConfig> {

  public ImporterCmd() {
    super("importer", "Imports the latest version of an external datasource into staging");
  }

  @Override
  protected void run(Bootstrap<CliConfig> bootstrap, Namespace namespace, CliConfig cfg) throws Exception {
    final int datasetKey = 1234;

    System.out.format("Normalizing %s!\n", datasetKey);
    //Normalizer normalizer = Normalizer.create(cfg, datasetKey, toEnum);
    //normalizer.run();

    System.out.format("Importing %s into Postgres!\n", datasetKey);
    //Importer importer = Importer.create(cfg, datasetKey, nameUsageService, usageService, sqlService);
    //importer.run();

    System.out.format("Analyzing %s!\n", datasetKey);
    DatasetMetricsMapper datasetMetricsMapper = null;
    Analyser analyser = Analyser.create(datasetKey, datasetMetricsMapper);
    analyser.run();

    System.out.format("Import %s completed!\n", datasetKey);
  }
}
