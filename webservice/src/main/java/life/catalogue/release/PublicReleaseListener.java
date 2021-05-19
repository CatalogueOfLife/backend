package life.catalogue.release;

import com.google.common.eventbus.Subscribe;

import life.catalogue.WsServerConfig;
import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.model.*;
import life.catalogue.api.search.ExportSearchRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;

import life.catalogue.dao.DatasetExportDao;

import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.ProjectSourceMapper;
import life.catalogue.doi.service.DatasetConverter;
import life.catalogue.doi.service.DoiService;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Class to listen to dataset changes and act if a COL release was changed from private to public.
 * It then copies existing exports to the COL export folder.
 */
public class PublicReleaseListener {
  private static final Logger LOG = LoggerFactory.getLogger(PublicReleaseListener.class);

  private final WsServerConfig cfg;
  private final SqlSessionFactory factory;
  private final DatasetExportDao dao;
  private final DoiService doiService;
  private final DatasetConverter converter;

  public PublicReleaseListener(WsServerConfig cfg, SqlSessionFactory factory, DatasetExportDao dao, DoiService doiService) {
    this.cfg = cfg;
    this.factory = factory;
    this.dao = dao;
    this.doiService = doiService;
    this.converter = new DatasetConverter(cfg.portalURI, cfg.clbURI);
  }

  @Subscribe
  public void datasetChanged(DatasetChanged event){
    if (event.isUpdated() // assures we got both obj and old
      && event.obj.getOrigin() == DatasetOrigin.RELEASED
      && event.old.isPrivat() // that was private before
      && !event.obj.isPrivat() // but now is public
    ) {

      // publish DOI if exists
      if (event.obj.getDoi() != null) {
        LOG.info("Publish DOI {}", event.obj.getDoi());
        doiService.publishSilently(event.obj.getDoi());
      }

      // COL specifics
      if (Datasets.COL == event.obj.getSourceKey()) {
        updateColDoiUrls(event.obj);
        copyExportsToColDownload(event.obj);
      }
    }
  }

  /**
   * Change DOI metadata for last release to point to CLB, not portal
   */
  void updateColDoiUrls(Dataset release) {
    try (SqlSession session = factory.openSession()) {
      Integer lastReleaseKey = ProjectRelease.findPreviousRelease(release.getSourceKey(), session);
      if (lastReleaseKey != null) {
        LOG.info("Change target URLs of DOIs from previous release {} to point to ChecklistBank", lastReleaseKey);
        DatasetMapper dm = session.getMapper(DatasetMapper.class);
        Dataset prev = dm.get(lastReleaseKey);
        doiService.updateSilently(prev.getDoi(), converter.datasetURI(lastReleaseKey, false));

        // sources
        ProjectSourceMapper psm = session.getMapper(ProjectSourceMapper.class);
        // track DOIs of current release - these should stay as they are!
        Set<DOI> currentDOIs = psm.listReleaseSources(release.getKey()).stream()
          .map(ArchivedDataset::getDoi)
          .filter(java.util.Objects::nonNull)
          .collect(Collectors.toSet());
        for (var src : psm.listReleaseSources(lastReleaseKey)) {
          if (src.getDoi() != null && !currentDOIs.contains(src.getDoi())) {
            doiService.updateSilently(src.getDoi(), converter.sourceURI(lastReleaseKey, src.getKey(), false));
          }
        }
      }
    }
  }

  public void copyExportsToColDownload(Dataset dataset) {
    if (cfg.release.colDownloadDir != null) {
      final int datasetKey = dataset.getKey();
      final LocalDate released = dataset.getReleased();
      if (released == null) {
        LOG.error("Updated COL release {} is missing a release date", datasetKey);
        return;
      }
      var resp = dao.list(ExportSearchRequest.fullDataset(datasetKey), new Page(0, 10));
      Set<DataFormat> done = new HashSet<>();
      for (DatasetExport exp : resp.getResult()) {
        if (!done.contains(exp.getRequest().getFormat())) {
          DataFormat df = exp.getRequest().getFormat();
          done.add(df);
          File target = colDownloadFile(cfg.release.colDownloadDir, released, df);
          File source = cfg.downloadFile(exp.getKey());
          if (source.exists()) {
            try {
              FileUtils.copyFile(source, target);
            } catch (IOException e) {
              LOG.error("Failed to copy COL {} export {} to {}", df, source, target, e);
            }
          } else {
            LOG.warn("COL {} export {} does not exist at expected location {}", df, exp.getKey(), source);
          }
        }

      }
    }
  }

  public static File colDownloadFile(File colDownloadDir, LocalDate released, DataFormat format) {
    String iso = DateTimeFormatter.ISO_DATE.format(released);
    return new File(colDownloadDir, iso + "_" + format + ".zip");
  }

}
