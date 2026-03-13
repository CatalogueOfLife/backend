package life.catalogue.release;

import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DatasetListener;
import life.catalogue.api.event.DoiChange;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Setting;
import life.catalogue.common.io.PathUtils;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.dao.DatasetExportDao;
import life.catalogue.dao.NameUsageArchiver;
import life.catalogue.db.mapper.DatasetMapper;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import life.catalogue.event.EventBroker;

import life.catalogue.exporter.ColReleaseExportJob;

import life.catalogue.exporter.ExporterConfig;

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
  public static Set<DataFormat> EXPORT_FORMATS = Set.of(DataFormat.TEXT_TREE, DataFormat.COLDP, DataFormat.DWCA);

  private final ReleaseConfig rCfg;
  private final ExporterConfig eCfg;
  private final SqlSessionFactory factory;
  private final NameUsageArchiver archiver;
  private final CloseableHttpClient httpClient;
  private final EventBroker bus;
  private final JobExecutor executor;

  public PublishReleaseListener(ReleaseConfig rCfg, ExporterConfig eCfg, SqlSessionFactory factory, CloseableHttpClient httpClient, JobExecutor executor, EventBroker bus) {
    this.rCfg = rCfg;
    this.eCfg = eCfg;
    this.factory = factory;
    this.executor = executor;
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
        // generate downloads for COL releases
        for (var format : EXPORT_FORMATS) {
          var expJob = new ColReleaseExportJob(event.obj.getKey(), event.user, true, format, rCfg, eCfg, factory);
          executor.submit(expJob);
        }

        // symlink latest logs
        if (event.obj.getAttempt() != null) {
          try {
            // set latest_logs -> /mnt/auto/col/releases/3/410
            File logs = rCfg.reportDir(Datasets.COL, event.obj.getAttempt());
            File symlink = new File(rCfg.colDownloadDir, ColReleaseExportJob.prefix(event.obj.getOrigin()) + "latest_logs");
            PathUtils.symlink(symlink, logs);
          } catch (IOException e) {
            LOG.error("Failed to symlink latest {} logs", event.obj.getOrigin(), e);
          }
        }

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
}
