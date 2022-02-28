package life.catalogue.release;

import life.catalogue.WsServerConfig;
import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.model.*;
import life.catalogue.api.search.ExportSearchRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.common.io.PathUtils;
import life.catalogue.dao.NameUsageArchiver;
import life.catalogue.dao.DatasetExportDao;
import life.catalogue.dao.DatasetSourceDao;
import life.catalogue.db.mapper.*;
import life.catalogue.doi.service.DatasetConverter;
import life.catalogue.doi.service.DoiException;
import life.catalogue.doi.service.DoiService;

import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.Subscribe;


/**
 * Class to listen to dataset changes and act if a COL release was changed from private to public.
 * It then
 *  - copies existing exports to the COL export folder
 *  - inserts deleted ids from the reports into the names archive
 *  - removes resurrected ids from the names archive
 */
public class PublicReleaseListener {
  private static final Logger LOG = LoggerFactory.getLogger(PublicReleaseListener.class);

  private final WsServerConfig cfg;
  private final SqlSessionFactory factory;
  private final DatasetExportDao dao;
  private final DoiService doiService;
  private final DatasetConverter converter;
  private final NameUsageArchiver archiver;

  public PublicReleaseListener(WsServerConfig cfg, SqlSessionFactory factory, DatasetExportDao dao, DoiService doiService, DatasetConverter converter) {
    this.cfg = cfg;
    this.factory = factory;
    this.dao = dao;
    this.doiService = doiService;
    this.converter = converter;
    this.archiver = new NameUsageArchiver(factory);
  }

  @Subscribe
  public void datasetChanged(DatasetChanged event){
    if (event.isUpdated() // assures we got both obj and old
      && event.obj.getOrigin() == DatasetOrigin.RELEASED
      && event.old.isPrivat() // that was private before
      && !event.obj.isPrivat() // but now is public
    ) {
      LOG.info("Publish release {} {} by user {}.", event.obj.getKey(), event.obj.getAliasOrTitle(), event.obj.getModifiedBy());
      // publish DOI if exists
      if (event.obj.getDoi() != null) {
        LOG.info("Publish DOI {}", event.obj.getDoi());
        doiService.publishSilently(event.obj.getDoi());
      }

      /**
       * When a release gets published we need to modify the projects names archive.
       * For deleted ids a new entry in the names archive needs to be created.
       * For resurrected ids we need to remove them from the archive.
       */
      archiver.buildRelease(event.obj.getSourceKey(), event.obj.getKey());

      // COL specifics
      if (Datasets.COL == event.obj.getSourceKey()) {
        publishColSourceDois(event.obj);
        updateColDoiUrls(event.obj);
        copyExportsToColDownload(event.obj, true);
      }
    }
  }

  private void publishColSourceDois(Dataset release) {
    LOG.info("Publish all draft source DOIs for COL release {}: {}", release.getKey(), release.getVersion());
    DatasetSourceDao dao = new DatasetSourceDao(factory);
    AtomicInteger published = new AtomicInteger(0);
    try (SqlSession session = factory.openSession(true)) {
      DatasetSourceMapper dsm = session.getMapper(DatasetSourceMapper.class);
      dao.list(release.getKey(), release, false).forEach(d -> {
        if (d.getDoi() == null) {
          LOG.error("COL source {} {} without a DOI", d.getKey(), d.getAlias());
        } else {
          final DOI doi = d.getDoi();
          try {
            var srcAttr = converter.source(d, null, release, true);
            doiService.update(srcAttr);
            doiService.publish(doi);
          } catch (DoiException e) {
            LOG.error("Error publishing DOI {} for COL source {} {}", doi, d.getKey(), d.getAlias(), e);
          }
        }
      });
    }
    LOG.info("Published {} draft source DOIs for COL release {}: {}", published, release.getKey(), release.getVersion());
  }

  /**
   * Change DOI metadata for last release to point to CLB, not life.catalogue.portal
   */
  void updateColDoiUrls(Dataset release) {
    try (SqlSession session = factory.openSession()) {
      Integer lastReleaseKey = session.getMapper(DatasetMapper.class).previousRelease(release.getKey());
      if (lastReleaseKey != null) {
        LOG.info("Last public release before {} is {}", release.getKey(), lastReleaseKey);
        LOG.info("Change target URLs of DOIs from previous release {} to point to ChecklistBank", lastReleaseKey);
        DatasetMapper dm = session.getMapper(DatasetMapper.class);
        Dataset prev = dm.get(lastReleaseKey);
        if (prev.getDoi() != null) {
          doiService.updateSilently(prev.getDoi(), converter.datasetURI(lastReleaseKey, false));
        }

        // sources
        DatasetSourceMapper psm = session.getMapper(DatasetSourceMapper.class);
        // track DOIs of current release - these should stay as they are!
        Set<DOI> currentDOIs = psm.listReleaseSources(release.getKey()).stream()
          .map(Dataset::getDoi)
          .filter(java.util.Objects::nonNull)
          .collect(Collectors.toSet());
        for (var src : psm.listReleaseSources(lastReleaseKey)) {
          if (src.getDoi() != null && !currentDOIs.contains(src.getDoi())) {
            doiService.updateSilently(src.getDoi(), converter.sourceURI(lastReleaseKey, src.getKey(), false));
          }
        }
      } else {
        LOG.info("No previous release before {}", release.getKey());
      }
    } catch (RuntimeException e) {
      LOG.error("Error updating previous DOIs", e);
    }
  }

  public void copyExportsToColDownload(Dataset dataset, boolean symLinkLatest) {
    if (cfg.release.colDownloadDir != null) {
      final int datasetKey = dataset.getKey();
      final int projectKey = dataset.getSourceKey();
      if (dataset.getIssued() == null) {
        LOG.error("Updated COL release {} is missing a release date", datasetKey);
        return;
      }
      var resp = dao.list(ExportSearchRequest.fullDataset(datasetKey), new Page(0, 10));
      Set<DataFormat> done = new HashSet<>();
      for (DatasetExport exp : resp.getResult()) {
        if (!done.contains(exp.getRequest().getFormat())) {
          DataFormat df = exp.getRequest().getFormat();
          done.add(df);
          File target = colDownloadFile(cfg.release.colDownloadDir, dataset, df);
          File source = cfg.downloadFile(exp.getKey());
          if (source.exists()) {
            try {
              FileUtils.copyFile(source, target);
              if (symLinkLatest) {
                File symlink = colLatestFile(cfg.release.colDownloadDir, df);
                PathUtils.symlink(symlink, target);
              }
            } catch (IOException e) {
              LOG.error("Failed to copy COL {} export {} to {}", df, source, target, e);
            }
          } else {
            LOG.warn("COL {} export {} does not exist at expected location {}", df, exp.getKey(), source);
          }
        }
      }
      if (symLinkLatest && dataset.getAttempt() != null) {
        try {
          // set latest_logs -> /srv/releases/3/50
          File logs = cfg.release.reportDir(projectKey, dataset.getAttempt());
          File symlink = new File(cfg.release.colDownloadDir, "latest_logs");
          PathUtils.symlink(symlink, logs);
        } catch (IOException e) {
          LOG.error("Failed to symlink latest release logs", e);
        }
      }
    }
  }

  public static File colDownloadFile(File colDownloadDir, Dataset dataset, DataFormat format) {
    String iso = DateTimeFormatter.ISO_DATE.format(dataset.getIssued().getDate());
    return new File(colDownloadDir, "monthly/" + iso + "_" + format.getName().toLowerCase() + ".zip");
  }

  public static File colLatestFile(File colDownloadDir, DataFormat format) {
    return new File(colDownloadDir, "latest_" + format.getName().toLowerCase() + ".zip");
  }

}
