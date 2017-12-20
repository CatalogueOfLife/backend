package org.col.commands.importer;

import com.zaxxer.hikari.HikariDataSource;
import io.dropwizard.cli.EnvironmentCommand;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.setup.Environment;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.Dataset;
import org.col.api.DatasetImport;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.ImportState;
import org.col.api.vocab.License;
import org.col.commands.CliApp;
import org.col.commands.config.CliConfig;
import org.col.commands.importer.dwca.Normalizer;
import org.col.commands.importer.neo.NeoDbFactory;
import org.col.commands.importer.neo.NormalizerStore;
import org.col.dao.DatasetDao;
import org.col.dao.Pager;
import org.col.db.MybatisFactory;
import org.col.db.mapper.DatasetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;

public class ImporterCmd extends EnvironmentCommand<CliConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(ImporterCmd.class);

  private CliConfig cfg;
  private DownloadUtil downloader;
  private SqlSessionFactory factory;
  private boolean force;

  public ImporterCmd() {
    super(new CliApp(),"import", "Imports the latest version of an external dataset into staging");
  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    // Adds import options
    subparser.addArgument("-k", "--key")
        .dest("key")
        .type(Integer.class)
        .required(false)
        .help("The key of the dataset to import");
    subparser.addArgument("--all")
        .dest("all")
        .type(Boolean.class)
        .setDefault(false)
        .setConst(true)
        .required(false)
        .help("Import all datasets");
    subparser.addArgument("--all-empty")
        .dest("empty")
        .type(Boolean.class)
        .setDefault(false)
        .setConst(true)
        .required(false)
        .help("Import all empty datasets");
    subparser.addArgument("--url")
        .dest("url")
        .type(String.class)
        .required(false)
        .help("Import a dataset from a URL, creating a new dataset entry");
    subparser.addArgument("-f", "--force")
        .dest("force")
        .type(Boolean.class)
        .setDefault(false)
        .setConst(true)
        .required(false)
        .help("Force import of an unchanged dataset");
  }

  @Override
  protected void run(Environment env, Namespace namespace, CliConfig cfg) throws Exception {
    this.cfg = cfg;

    // create new datasource and http client
    try (HikariDataSource ds = cfg.db.pool();
         CloseableHttpClient hc = new HttpClientBuilder(env)
                 .using(cfg.client)
                 .build(getName())
    ){
      downloader = new DownloadUtil(hc);
      factory = MybatisFactory.configure(ds, "importer");

      force = namespace.getBoolean("force");
      final boolean all = namespace.getBoolean("all");
      final boolean empty = namespace.getBoolean("empty");
      if (all) {
        for (Dataset d : Pager.datasets(factory, false)) {
          importDataset(d);
        }

      } else if (empty) {
          for (Dataset d : Pager.datasets(factory, true)){
            importDataset(d);
          }

      } else {

        Dataset d;
        try (SqlSession session = factory.openSession()) {
          DatasetMapper dmapper = session.getMapper(DatasetMapper.class);
          String url = namespace.getString("url");
          if (url != null) {
            // new dataset with given url
            d = buildDataset(url);
            dmapper.create(d);
            session.commit();

          } else {
            // get by key
            int datasetKey = namespace.getInt("key");
            d = dmapper.get(datasetKey);
            if (d == null) {
              throw new IllegalArgumentException("Dataset " + datasetKey + " not existing");

            } else if (d.getDataAccess() == null) {
              throw new IllegalStateException("Dataset " + datasetKey + " has no external datasource configured");
            }
          }
        }
        importDataset(d);
      }
    }
  }

  private Dataset buildDataset(String url) {
    Dataset d = new Dataset();
    d.setTitle(url);
    d.setDataFormat(DataFormat.DWCA);
    d.setDataAccess(URI.create(url));
    d.setLicense(License.UNSPECIFIED);
    return d;
  }

  private void importDataset(Dataset d) throws Exception {
    final int datasetKey = d.getKey();
    final File dwcaDir = cfg.normalizer.sourceDir(datasetKey);
    NormalizerStore store = null;
    DatasetImport di;

    try (SqlSession session = factory.openSession(true)){
      LOG.info("Start import of dataset {} from {}", datasetKey, d.getDataAccess());
      DatasetDao dao = new DatasetDao(session);
      di = dao.startImport(d);
    }

    // keep track which larger step failed
    ImportState state = ImportState.FAILED_DOWNLOAD;
    try {
      LOG.info("Downloading sources for dataset {} from {}", datasetKey, d.getDataAccess());
      File dwca = cfg.normalizer.source(datasetKey);
      dwca.getParentFile().mkdirs();

      final boolean isModified = downloader.downloadIfModified(d.getDataAccess(), dwca);
      if(isModified || force) {
        if (!isModified) {
          LOG.info("Force reimport of unchanged archive {}", datasetKey);
        }
        di.setDownload(downloader.lastModified(dwca));

        LOG.info("Extracting files from archive {}", datasetKey);
        CompressionUtil.decompressFile(dwcaDir, dwca);

        LOG.info("Normalizing {}!", datasetKey);
        state = ImportState.FAILED_NORMALIZER;
        store = NeoDbFactory.create(cfg.normalizer, datasetKey);
        new Normalizer(store, dwcaDir).run();

        LOG.info("Writing {} to Postgres!", datasetKey);
        state = ImportState.FAILED_PGIMPORT;
        store = NeoDbFactory.open(cfg.normalizer, datasetKey);
        new PgImport(datasetKey, store, factory, cfg.importer).run();

        LOG.info("Build import metrics for dataset {}", di.getDatasetKey());
        state = ImportState.FAILED_METRICS;
        try (SqlSession session = factory.openSession(true)){
          new DatasetDao(session).updateImportSuccess(di);
        }

        LOG.info("Dataset import {} completed in {}", datasetKey,
            DurationFormatUtils.formatDurationHMS(Duration.between(di.getStarted(), LocalDateTime.now()).toMillis())
        );

      } else {
        LOG.info("Dataset {} sources unchanged. Stop import", datasetKey);
        di.setDownload(downloader.lastModified(dwca));
        try (SqlSession session = factory.openSession(true)){
          new DatasetDao(session).updateImportUnchanged(di);
        }
      }

    } catch (Exception e) {
      // failed import
      LOG.error("Dataset {} import failed. Log to pg.", datasetKey, e);
      try (SqlSession session = factory.openSession(true)){
        new DatasetDao(session).updateImportFailure(di, state, e);
        session.commit();
      }

    } finally {
      // close neo store if open
      if (store != null) {
        store.close();
        // delete it
        File storeDir = cfg.normalizer.neoDir(datasetKey);
        LOG.debug("Remove NormalizerStore at {}", storeDir);
        FileUtils.deleteDirectory(storeDir);
      }
      // remove decompressed dwca folder
      LOG.debug("Remove uncompressed dwca dir {}", dwcaDir.getAbsolutePath());
      FileUtils.deleteDirectory(dwcaDir);
    }
  }
}
