package life.catalogue.release;

import life.catalogue.WsServerConfig;
import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.model.*;
import life.catalogue.api.search.ExportSearchRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.IdReportType;
import life.catalogue.common.id.IdConverter;
import life.catalogue.common.io.PathUtils;
import life.catalogue.dao.DatasetExportDao;
import life.catalogue.dao.DatasetSourceDao;
import life.catalogue.dao.NameDao;
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
import org.apache.ibatis.session.ExecutorType;
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

  public PublicReleaseListener(WsServerConfig cfg, SqlSessionFactory factory, DatasetExportDao dao, DoiService doiService, DatasetConverter converter) {
    this.cfg = cfg;
    this.factory = factory;
    this.dao = dao;
    this.doiService = doiService;
    this.converter = converter;
  }

  @Subscribe
  public void datasetChanged(DatasetChanged event){
    if (event.isUpdated() // assures we got both obj and old
      && event.obj.getOrigin() == DatasetOrigin.RELEASED
      && event.old.isPrivat() // that was private before
      && !event.obj.isPrivat() // but now is public
    ) {
      Integer lastReleaseKey;
      try (SqlSession session = factory.openSession(true)) {
        lastReleaseKey = ProjectRelease.findPreviousRelease(event.old.getSourceKey(), session);
      }
      LOG.info("Publish release {} {} by user {}. Last public release was {}", event.obj.getKey(), event.obj.getAliasOrTitle(), event.obj.getModifiedBy(), lastReleaseKey);
      // publish DOI if exists
      if (event.obj.getDoi() != null) {
        LOG.info("Publish DOI {}", event.obj.getDoi());
        doiService.publishSilently(event.obj.getDoi());
      }

      updateNamesArchive(event.obj, lastReleaseKey);

      // COL specifics
      if (Datasets.COL == event.obj.getSourceKey()) {
        publishColSourceDois(event.obj);
        updateColDoiUrls(event.obj, lastReleaseKey);
        copyExportsToColDownload(event.obj, true);
      }
    }
  }

  /**
   * When a release gets published we need to modify the projects names archive.
   * For deleted ids a new entry in the names archive needs to be created.
   * For resurrected ids we need to remove them from the archive.
   */
  private void updateNamesArchive(Dataset d, Integer lastReleaseKey) {
    LOG.info("Update names archive from id reports for {}", d.getKey());
    try (SqlSession session = factory.openSession(true);
         SqlSession batchSession = factory.openSession(ExecutorType.BATCH, false)
    ) {
      var idm = session.getMapper(IdReportMapper.class);
      var tm = session.getMapper(TaxonMapper.class);
      var rm = session.getMapper(ReferenceMapper.class);
      var num = session.getMapper(NameUsageMapper.class);
      var anm = batchSession.getMapper(ArchivedNameMapper.class);
      final int projectKey = d.getSourceKey();
      final DSID<String> archiveKey = DSID.root(projectKey);
      int counter = 0;
      for (IdReportEntry r : idm.processDataset(d.getKey())) {
        if (r.getType() != IdReportType.CREATED) {
          final String id = IdConverter.LATIN29.encode(r.getId());

          if (r.getType() == IdReportType.RESURRECTED) {
            anm.delete(archiveKey.id(id));

          } else if (r.getType() == IdReportType.DELETED) {
            var oldKey = DSID.of(lastReleaseKey, id);
            var u = num.get(oldKey);
            // assemble archived usage
            ArchivedNameUsage au = new ArchivedNameUsage(u);
            // basionym
            var bas = NameDao.getBasionym(factory, oldKey);
            if (bas != null) {
              au.setBasionym(new SimpleName(bas));
            }
            // publishedIn
            if (u.getName().getPublishedInId() != null) {
              var pub = rm.get(DSID.of(lastReleaseKey, u.getName().getPublishedInId()));
              if (pub != null) {
                au.setPublishedIn(pub.getCitation());
              }
            }
            // classification
            au.setClassification(tm.classificationSimple(oldKey));
            // lastReleaseKey
            au.setLastReleaseKey(lastReleaseKey);
            // firstReleaseKey
            var first = idm.first(projectKey, r.getId());
            if (first == null) {
              LOG.warn("Deleted ID {} missing created IdReport event", r.getId());
            } else {
              au.setFirstReleaseKey(first.getDatasetKey());
            }

            if (u.isTaxon()){
              // extinct
              Taxon t = (Taxon) u;
              au.setExtinct(t.isExtinct());

            } else if (u.isBareName()) {
              LOG.warn("{} stable ID {} is a {}. Skip", r.getType(), id, u.getStatus());
              return;
            }

            anm.create(au);
            if (counter++ % 5000 == 0) {
              batchSession.commit();
            }
          }
        }
      }
      batchSession.commit();
      LOG.info("Copied {} name usages into the project archive as their stable IDs were deleted in release {}", counter, d.getKey());
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
  void updateColDoiUrls(Dataset release, Integer lastReleaseKey) {
    try (SqlSession session = factory.openSession()) {
      if (lastReleaseKey != null) {
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
