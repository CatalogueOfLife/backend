package org.col.task.importer;

import com.google.common.collect.ImmutableMultimap;
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
import org.col.dao.DatasetDao;
import org.col.dao.Pager;
import org.col.db.mapper.DatasetMapper;
import org.col.task.common.BaseTask;
import org.col.task.common.TaskServerConfig;
import org.col.task.importer.dwca.Normalizer;
import org.col.task.importer.neo.NeoDbFactory;
import org.col.task.importer.neo.NormalizerStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.PrintWriter;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Imports the latest version of an external dataset into staging
 */
public class ImporterTask extends BaseTask {
  private static final Logger LOG = LoggerFactory.getLogger(ImporterTask.class);

  private final TaskServerConfig cfg;
  private final DownloadUtil downloader;
  private final SqlSessionFactory factory;

  public ImporterTask(TaskServerConfig cfg, SqlSessionFactory factory, CloseableHttpClient client) {
    super("import");
    this.cfg = cfg;
    this.downloader = new DownloadUtil(client);
    this.factory = factory;
  }

  @Override
  public void run(ImmutableMultimap<String, String> params, PrintWriter out) throws Exception {
    // create new datasource and http client
    boolean force = getFirstBoolean( "force", params,false);
    final boolean all = getFirstBoolean("all", params,false);
    final boolean empty = getFirstBoolean("empty", params,false);
    if (all) {
      for (Dataset d : Pager.datasets(factory, false)) {
        importDataset(d, force);
      }

    } else if (empty) {
        for (Dataset d : Pager.datasets(factory, true)){
          importDataset(d, force);
        }

    } else {

      Dataset d;
      try (SqlSession session = factory.openSession()) {
        DatasetMapper dmapper = session.getMapper(DatasetMapper.class);
        if (params.containsKey("key")) {
          // get by key
          for (String key : params.get("key")) {
            int datasetKey = Integer.valueOf(key);
            d = dmapper.get(datasetKey);
            if (d == null) {
              throw new IllegalArgumentException("Dataset " + datasetKey + " not existing");

            } else if (d.getDataAccess() == null) {
              throw new IllegalStateException("Dataset " + datasetKey + " has no external datasource configured");
            }
          }

        } else if (params.containsKey("url")) {
          // create new datasets with the provided URL
          for (String url : params.get("url")) {
            if (url != null) {
              // new dataset with given url
              d = buildDataset(url);
              dmapper.create(d);
              session.commit();
              importDataset(d, force);
            }
          }
        }
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

  private void importDataset(Dataset d, boolean force) throws Exception {
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
