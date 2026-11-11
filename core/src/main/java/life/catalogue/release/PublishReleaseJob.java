package life.catalogue.release;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Setting;
import life.catalogue.common.io.PathUtils;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.config.ReleaseAction;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.dao.NameUsageArchiver;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.doi.service.DoiConfig;
import life.catalogue.exporter.ColReleaseExportJob;
import life.catalogue.exporter.ExporterConfig;
import life.catalogue.matching.UsageMatcherFactory;
import life.catalogue.outbox.DoiAction;
import life.catalogue.outbox.Outbox;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The work that follows a release becoming public, submitted once by the RELEASE_PUBLISHED outbox command:
 * <ul>
 *   <li>for COL: builds the COL exports, maps the identifiers of retired COL editions to an extended release,
 *   links the latest logs and updates the DOI of the previous release so it no longer resolves as the latest</li>
 *   <li>archives the name usages of the release, see NameUsageArchiver</li>
 *   <li>fires the publish actions of the project's release config</li>
 * </ul>
 * The concept DOI is published by the outbox DOI command the update itself requested.
 *
 * The steps are independent: a failing one does not stop the others, the job then fails with all their errors.
 * It is never retried by itself, so the publish actions are not fired twice. An incomplete archive is repaired with
 * the ArchiveRefreshJob.
 */
public class PublishReleaseJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(PublishReleaseJob.class);
  /** ordered: the cheap TextTree lands on the download server first, the two big archives follow */
  public static final List<DataFormat> EXPORT_FORMATS = List.of(DataFormat.TEXT_TREE, DataFormat.COLDP, DataFormat.DWCA);

  private final int releaseKey;
  private final ReleaseConfig rCfg;
  private final ExporterConfig eCfg;
  private final DoiConfig dCfg;
  private final SqlSessionFactory factory;
  private final UsageMatcherFactory matcherFactory;
  private final CloseableHttpClient httpClient;
  private final JobExecutor executor;
  private final List<Exception> errors = new ArrayList<>();

  public PublishReleaseJob(int releaseKey, int userKey, ReleaseConfig rCfg, ExporterConfig eCfg, DoiConfig dCfg,
                           SqlSessionFactory factory, UsageMatcherFactory matcherFactory, CloseableHttpClient httpClient,
                           JobExecutor executor) {
    super(userKey);
    this.releaseKey = releaseKey;
    this.rCfg = rCfg;
    this.eCfg = eCfg;
    this.dCfg = dCfg;
    this.factory = factory;
    this.matcherFactory = matcherFactory;
    this.httpClient = httpClient;
    this.executor = executor;
  }

  @Override
  public Integer datasetKey() {
    return releaseKey;
  }

  @Override
  public Object getParams() {
    return Map.of("releaseKey", releaseKey);
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    return other instanceof PublishReleaseJob prj && prj.releaseKey == releaseKey;
  }

  @Override
  public void execute() throws Exception {
    final Dataset release;
    try (SqlSession session = factory.openSession(true)) {
      release = session.getMapper(DatasetMapper.class).get(releaseKey);
    }
    if (release == null || release.getDeleted() != null || release.isPrivat() || !release.getOrigin().isRelease()) {
      LOG.warn("Dataset {} is no public release (anymore), nothing to publish", releaseKey);
      return;
    }
    LOG.info("Publish {} {} {} from project {}", release.getOrigin(), releaseKey, release.getAliasOrTitle(), release.getSourceKey());

    if (Datasets.COL == release.getSourceKey()) {
      publishCOL(release);
    }

    // the published release enters the project's name usage archive. It is safe to run twice
    setStep("archiving names");
    step("archive names", () -> new NameUsageArchiver(factory, new IgnoredReleases(factory)).archiveRelease(releaseKey));

    // generic hooks for releases
    setStep("publish actions");
    step("run publish actions", () -> {
      ProjectReleaseConfig prCfg = null;
      try (SqlSession session = factory.openSession(true)) {
        var settings = session.getMapper(DatasetMapper.class).getSettings(release.getSourceKey());
        Setting relSetting = release.getOrigin() == DatasetOrigin.XRELEASE ? Setting.XRELEASE_CONFIG : Setting.RELEASE_CONFIG;
        if (settings.containsKey(relSetting)) {
          prCfg = ProjectRelease.loadConfig(ProjectReleaseConfig.class, settings.getURI(relSetting), false);
        }
      }
      if (prCfg != null && prCfg.publishActions != null) {
        ReleaseAction.callAll(prCfg.publishActions, httpClient, release, "publish actions");
      }
    });

    if (!errors.isEmpty()) {
      var e = new IllegalStateException(errors.size() + " publishing steps failed for release " + releaseKey + ": "
        + errors.getFirst().getMessage());
      errors.forEach(e::addSuppressed);
      throw e;
    }
  }

  private void publishCOL(Dataset release) {
    LOG.info("Publish COL {} specifics", release.getOrigin());
    setStep("COL exports");
    // generate downloads for COL releases. One job for all formats - they all block on this release's
    // dataset lock anyway, so as separate jobs they mostly sat in the executor's backoff sleep.
    step("submit COL exports", () -> executor.submit(
      new ColReleaseExportJob(releaseKey, getUserKey(), true, EXPORT_FORMATS, rCfg, eCfg, factory)
    ));
    // map the ids of retired COL editions to the extended release, one dated file per release
    // and a stable latest symlink, e.g. xr_latest_col19-idmap.tsv.gz
    if (release.getOrigin() == DatasetOrigin.XRELEASE) {
      for (var map : rCfg.legacyIdMaps) {
        step("map " + map.name + " ids", () -> executor.submit(
          new LegacyIdMapJob(map, releaseKey, getUserKey(), rCfg.colDownloadDir, true, factory, matcherFactory)
        ));
      }
    }
    // symlink latest logs, e.g. latest_logs -> /mnt/auto/col/releases/3/410
    if (release.getAttempt() != null) {
      step("symlink latest logs", () -> {
        File logs = rCfg.reportDir(Datasets.COL, release.getAttempt());
        File symlink = new File(rCfg.colDownloadDir, ColReleaseExportJob.prefix(release.getOrigin()) + "latest_logs");
        PathUtils.symlink(symlink, logs);
      });
    }
    // update DOI URL for the previously-latest release so it no longer resolves as "latest"
    step("update DOI of previous release", () -> {
      try (SqlSession session = factory.openSession(true)) {
        var dm = session.getMapper(DatasetMapper.class);
        var prevRelKey = dm.previousRelease(releaseKey);
        if (prevRelKey != null) {
          var prevRel = dm.get(prevRelKey);
          if (prevRel != null && prevRel.getDoi() != null) {
            Outbox.doi(session, dCfg, prevRel.getDoi(), DoiAction.UPDATE, getUserKey());
          }
        }
      }
    });
  }

  private interface Step {
    void run() throws Exception;
  }

  private void step(String name, Step step) {
    try {
      step.run();
    } catch (Exception e) {
      LOG.error("Failed to {} for release {}", name, releaseKey, e);
      errors.add(e);
    }
  }
}
