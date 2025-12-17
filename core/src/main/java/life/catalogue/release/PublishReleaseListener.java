package life.catalogue.release;

import com.google.common.eventbus.EventBus;

import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DatasetListener;
import life.catalogue.api.event.DoiChange;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetExport;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.ExportSearchRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Setting;
import life.catalogue.common.io.PathUtils;
import life.catalogue.concurrent.JobConfig;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.dao.DatasetExportDao;
import life.catalogue.dao.NameUsageArchiver;
import life.catalogue.db.mapper.DatasetMapper;

import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import life.catalogue.event.EventBroker;

import org.apache.commons.io.FileUtils;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Class to listen to dataset changes and act if a non project dataset was changed from private to public.
 * It then
 *  - publishes the DOI & Version DOI
 * For COL releases it also does:
 *  - copies existing exports to the COL export folder
 *  - inserts deleted ids from the reports into the names archive
 *  - removes resurrected ids from the names archive
 */
public class PublishReleaseListener implements DatasetListener {
  private static final Logger LOG = LoggerFactory.getLogger(PublishReleaseListener.class);

  private final ReleaseConfig cfg;
  private final JobConfig jCfg;
  private final SqlSessionFactory factory;
  private final DatasetExportDao dao;
  private final NameUsageArchiver archiver;
  private final CloseableHttpClient httpClient;
  private final EventBroker bus;

  public PublishReleaseListener(ReleaseConfig rCfg, JobConfig jCfg, SqlSessionFactory factory, CloseableHttpClient httpClient, DatasetExportDao dao, EventBroker bus) {
    this.cfg = rCfg;
    this.jCfg = jCfg;
    this.factory = factory;
    this.dao = dao;
    this.httpClient = httpClient;
    this.archiver = new NameUsageArchiver(factory);
    this.bus = bus;
  }

  @Override
  public void datasetChanged(DatasetChanged event){
    if (event.isUpdated() // assures we got both obj and old
      && event.obj.getOrigin().isRelease() // only for releases
      && event.old.isPrivat() // that was private before
      && !event.obj.isPrivat() // but now is public
    ) {

      LOG.info("Publish {} {} {} from project {} by user {}.", event.obj.getOrigin(), event.obj.getKey(), event.obj.getAliasOrTitle(), event.obj.getSourceKey(), event.obj.getModifiedBy());
      // COL release specifics
      if (Datasets.COL == event.obj.getSourceKey() && event.obj.getOrigin().isRelease()) {
        LOG.info("Publish COL {} specifics", event.obj.getOrigin());
        copyExportsToColDownload(event.obj, true);
        // update DOI URL for last release
        try (SqlSession session = factory.openSession(true)) {
          var prevRelKey = session.getMapper(DatasetMapper.class).previousRelease(event.obj.getKey());
          var prev = new Dataset();
          prev.setKey(prevRelKey);
          bus.publish(DoiChange.update(prev.getDoi())); // the DOI is built only from the key
        }
      }

      // When a release gets published we need to modify the projects name archive:
      // a) Usages with new ids need to be added
      // b) For all still existing usages the release_key needs to be added
      try {
        archiver.archiveRelease(event.obj.getKey(), true);
      } catch (Exception e) {
        LOG.error("Failed to archive names for published release {}", event.obj.getKey(), e);
      }

      // generic hooks for releases?
      ProjectReleaseConfig rcfg = null;
      try (SqlSession session = factory.openSession(true)) {
        var settings = session.getMapper(DatasetMapper.class).getSettings(event.obj.getSourceKey());
        Setting relSetting = event.obj.getOrigin() == DatasetOrigin.XRELEASE ? Setting.XRELEASE_CONFIG : Setting.RELEASE_CONFIG;
        if (settings.containsKey(relSetting)) {
          rcfg = ProjectRelease.loadConfig(ProjectReleaseConfig.class, settings.getURI(relSetting), false);
        }
      } catch (Exception e) {
        LOG.error("Failed to look for custom publishing actions for release {}", event.obj.getKey(), e);
      }
      if (rcfg != null &&   rcfg.publishActions != null) {
        for (var action : rcfg.publishActions) {
          action.call(httpClient, event.obj);
        }
      }
    }
  }

  public void copyExportsToColDownload(Dataset dataset, boolean symLinkLatest) {
    if (cfg.colDownloadDir != null) {
      final int datasetKey = dataset.getKey();
      final int projectKey = dataset.getSourceKey();
      if (dataset.getIssued() == null) {
        LOG.error("Updated COL release {} is missing a release date", datasetKey);
        return;
      }
      var resp = dao.list(ExportSearchRequest.fullDataset(datasetKey), new Page(0, 50));
      Set<DataFormat> done = new HashSet<>();
      for (DatasetExport exp : resp.getResult()) {
        if (!done.contains(exp.getRequest().getFormat())) {
          DataFormat df = exp.getRequest().getFormat();
          done.add(df);
          copyExportToColDownload(dataset, df, exp.getKey(), symLinkLatest);
        }
      }
      if (symLinkLatest && dataset.getAttempt() != null) {
        try {
          // set latest_logs -> /mnt/auto/col/releases/3/410
          File logs = cfg.reportDir(projectKey, dataset.getAttempt());
          File symlink = new File(cfg.colDownloadDir, prefix(dataset) + "latest_logs");
          PathUtils.symlink(symlink, logs);
        } catch (IOException e) {
          LOG.error("Failed to symlink latest {} logs", dataset.getOrigin(), e);
        }
      }
      LOG.info("Copied {} COL exports to downloads at {}", done.size(), cfg.colDownloadDir);
    }
  }

  private static String prefix(Dataset dataset) {
    return dataset.getOrigin() == DatasetOrigin.XRELEASE ? "xr_" : "";
  }

  public void copyExportToColDownload(Dataset dataset, DataFormat df, UUID exportKey, boolean symLinkLatest) {
    File target = colDownloadFile(cfg.colDownloadDir, dataset, df);
    File source = jCfg.downloadFile(exportKey);
    if (source.exists()) {
      try {
        LOG.info("Copy COL {} export {} to {}", df, exportKey, target);
        FileUtils.copyFile(source, target);
        if (symLinkLatest) {
          File symlink = colLatestFile(cfg.colDownloadDir, dataset, df);
          LOG.info("Symlink COL {} export {} at {} to {}", df, exportKey, target, symlink);
          PathUtils.symlink(symlink, target);
        }
      } catch (IOException e) {
        LOG.error("Failed to copy COL {} export {} to {}", df, source, target, e);
      }
    } else {
      LOG.warn("COL {} export {} does not exist at expected location {}", df, exportKey, source);
    }
  }

  private static File colDownloadFile(File colDownloadDir, Dataset dataset, DataFormat format) {
    String iso = DateTimeFormatter.ISO_DATE.format(dataset.getIssued().getDate());
    return new File(colDownloadDir, "monthly/" + iso + "_" + prefix(dataset) + format.getFilename() + ".zip");
  }

  private static File colLatestFile(File colDownloadDir, Dataset dataset, DataFormat format) {
    return new File(colDownloadDir, prefix(dataset) + "latest_" + format.getFilename() + ".zip");
  }

}
