package org.col.commands.importer;

import com.zaxxer.hikari.HikariDataSource;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.Dataset;
import org.col.commands.config.CliConfig;
import org.col.commands.importer.dwca.Normalizer;
import org.col.commands.importer.neo.NeoDbFactory;
import org.col.commands.importer.neo.NormalizerStore;
import org.col.db.MybatisFactory;
import org.col.db.mapper.DatasetMapper;
import org.col.db.mapper.DatasetMetricsMapper;
import org.gbif.util.DownloadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class ImporterCmd extends ConfiguredCommand<CliConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(ImporterCmd.class);

  public ImporterCmd() {
    super("import", "Imports the latest version of an external dataset into staging");
  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    // Adds import options
    subparser.addArgument("-k", "--key")
        .dest("key")
        .type(Integer.class)
        .required(true)
        .help("The key of the dataset to import");
  }

  @Override
  protected void run(Bootstrap<CliConfig> bootstrap, Namespace namespace, CliConfig cfg) throws Exception {
    final int datasetKey = namespace.getInt("key");

    // create datasource
    HikariDataSource ds = cfg.db.pool();
    try {
      SqlSessionFactory factory = MybatisFactory.configure(ds, "importer");
      // read dataset
      Dataset d;
      try (SqlSession session = factory.openSession(true)){
        DatasetMapper datasetMapper = session.getMapper(DatasetMapper.class);
        d = datasetMapper.get(datasetKey);
        if (d == null) {
          throw new IllegalArgumentException("Dataset " + datasetKey + " not existing");

        } else if (d.getDataAccess() == null) {
          throw new IllegalStateException("Dataset " + datasetKey + " has no external datasource configured");
        }
      }

      LOG.info("Downloading sources for dataset {} from {}", datasetKey, d.getDataAccess());
      File dwca = cfg.normalizer.source(datasetKey);
      dwca.getParentFile().mkdirs();
      DownloadUtil.download(d.getDataAccess().toURL(), dwca);

      LOG.info("Extracting files from archive {}", datasetKey);
      File dwcaDir = cfg.normalizer.sourceDir(datasetKey);
      dwcaDir.mkdirs();
      CompressionUtil.decompressFile(dwcaDir, dwca);

      LOG.info("Normalizing {}!", datasetKey);
      NormalizerStore store = NeoDbFactory.create(cfg.normalizer, datasetKey);
      Normalizer normalizer = new Normalizer(store, dwcaDir);
      normalizer.run();

      LOG.info("Importing {} into Postgres!", datasetKey);
      Importer importer = new Importer(datasetKey,
          NeoDbFactory.open(cfg.normalizer, datasetKey), factory, cfg.importer
      );
      importer.run();

      LOG.info("Analyzing {}!", datasetKey);
      try (SqlSession session = factory.openSession(true)){
        DatasetMetricsMapper datasetMetricsMapper = session.getMapper(DatasetMetricsMapper.class);
        Analyser analyser = Analyser.create(datasetKey, datasetMetricsMapper);
        analyser.run();
      }

      LOG.info("Import {} completed!", datasetKey);

    } catch (Exception e){
      LOG.error("Import of dataset " +datasetKey + " failed", e);
      throw e;

    } finally {
      ds.close();
    }
  }
}
