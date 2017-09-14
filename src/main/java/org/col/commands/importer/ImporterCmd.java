package org.col.commands.importer;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.col.config.ColAppConfig;
import org.col.db.mapper.DatasourceMetricsMapper;

public class ImporterCmd extends ConfiguredCommand<ColAppConfig> {

  public ImporterCmd() {
    super("importer", "Imports the latest version of an external datasource into staging");
  }

  @Override
  protected void run(Bootstrap<ColAppConfig> bootstrap, Namespace namespace, ColAppConfig cfg) throws Exception {
    final int datasetKey = 1234;

    System.out.format("Normalizing %s!\n", datasetKey);
    //Normalizer normalizer = Normalizer.create(cfg, datasetKey, toEnum);
    //normalizer.run();

    System.out.format("Importing %s into Postgres!\n", datasetKey);
    //Importer importer = Importer.create(cfg, datasetKey, nameUsageService, usageService, sqlService);
    //importer.run();

    System.out.format("Analyzing %s!\n", datasetKey);
    DatasourceMetricsMapper datasourceMetricsMapper = null;
    Analyser analyser = Analyser.create(datasetKey, datasourceMetricsMapper);
    analyser.run();

    System.out.format("Import %s completed!\n", datasetKey);
  }
}
