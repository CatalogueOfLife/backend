package life.catalogue.release;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.Setting;
import life.catalogue.cache.VarnishUtils;
import life.catalogue.common.date.DateUtils;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.common.text.CitationUtils;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.dao.*;
import life.catalogue.db.mapper.CitationMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetSourceMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.validation.Validator;
import javax.ws.rs.core.UriBuilder;

import org.apache.commons.io.FileUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProjectRelease extends AbstractProjectCopy {
  private static final Logger LOG = LoggerFactory.getLogger(ProjectRelease.class);
  public static Set<DataFormat> EXPORT_FORMATS = Set.of(DataFormat.TEXT_TREE, DataFormat.COLDP, DataFormat.DWCA);
  private static final String DEFAULT_ALIAS_TEMPLATE = "{aliasOrTitle}-{date}";
  private static final String DEFAULT_VERSION_TEMPLATE = "{date}";

  protected final WsServerConfig cfg;
  protected final ReferenceDao rDao;
  protected final NameDao nDao;
  protected final SectorDao sDao;
  private final UriBuilder datasetApiBuilder;
  private final URI portalURI;
  private final CloseableHttpClient client;
  private final ImageService imageService;
  private final ExportManager exportManager;
  private final DoiService doiService;
  private final DoiUpdater doiUpdater;
  private Integer prevReleaseKey;

  ProjectRelease(SqlSessionFactory factory, NameUsageIndexService indexService, ImageService imageService,
                 DatasetImportDao diDao, DatasetDao dDao, ReferenceDao rDao, NameDao nDao, SectorDao sDao,
                 int datasetKey, int userKey, WsServerConfig cfg, CloseableHttpClient client, ExportManager exportManager,
                 DoiService doiService, DoiUpdater doiUpdater, Validator validator) {
    this("releasing", factory, indexService, imageService, diDao, dDao, rDao, nDao, sDao, datasetKey, userKey, cfg, client, exportManager, doiService, doiUpdater, validator);
  }

  ProjectRelease(String action, SqlSessionFactory factory, NameUsageIndexService indexService, ImageService imageService,
                 DatasetImportDao diDao, DatasetDao dDao, ReferenceDao rDao, NameDao nDao, SectorDao sDao,
                 int datasetKey, int userKey, WsServerConfig cfg, CloseableHttpClient client, ExportManager exportManager,
                 DoiService doiService, DoiUpdater doiUpdater, Validator validator) {
    super(action, factory, diDao, dDao, indexService, validator, userKey, datasetKey, true, cfg.release.deleteOnError);
    this.imageService = imageService;
    this.doiService = doiService;
    this.rDao = rDao;
    this.nDao = nDao;
    this.sDao = sDao;
    this.cfg = cfg;
    String latestRelease = String.format("L%sR", getClass().equals(XRelease.class) ? "X" : "");
    this.datasetApiBuilder = cfg.apiURI == null ? null : UriBuilder.fromUri(cfg.apiURI).path("dataset/{key}"+latestRelease);
    this.portalURI = cfg.apiURI == null ? null : UriBuilder.fromUri(cfg.apiURI).path("portal").build();
    this.client = client;
    this.exportManager = exportManager;
    this.doiUpdater = doiUpdater;
    // point to release in CLB - this requires the datasetKey to exist already
    newDataset.setUrl(UriBuilder.fromUri(cfg.clbURI)
                       .path("dataset")
                       .path(newDataset.getKey().toString())
                       .build());
    dDao.update(newDataset, userKey);
  }

  @Override
  protected void modifyDataset(Dataset d, DatasetSettings ds) {
    super.modifyDataset(d, ds);
    modifyDatasetForRelease(d, ds);
  }

  public static void modifyDatasetForRelease(Dataset d, DatasetSettings ds) {
    d.setOrigin(DatasetOrigin.RELEASE);

    final FuzzyDate today = FuzzyDate.now();
    d.setIssued(today);

    String version = CitationUtils.fromTemplate(d, ds, Setting.RELEASE_VERSION_TEMPLATE, DEFAULT_VERSION_TEMPLATE);
    d.setVersion(version);

    String alias = CitationUtils.fromTemplate(d, ds, Setting.RELEASE_ALIAS_TEMPLATE, DEFAULT_ALIAS_TEMPLATE);
    d.setAlias(alias);

    // all releases are private candidate releases first
    d.setPrivat(true);
  }

  /**
   * @param dKey datasetKey to remove name & reference orphans from.
   */
  void removeOrphans(int dKey) {
    final LocalDateTime start = LocalDateTime.now();
    nDao.deleteOrphans(dKey, null, user);
    rDao.deleteOrphans(dKey, null, user);
    DateUtils.logDuration(LOG, "Removing orphans", start);
  }

  @Override
  void prepWork() throws Exception {
    LocalDateTime start = LocalDateTime.now();

    if (settings.isEnabled(Setting.RELEASE_REMOVE_BARE_NAMES)) {
      removeOrphans(datasetKey);
    }

    prevReleaseKey = createReleaseDOI();
    DateUtils.logDuration(LOG, "Preparing release", start);

    // map ids
    start = LocalDateTime.now();
    updateState(ImportState.MATCHING);
    IdProvider idProvider = new IdProvider(datasetKey, attempt, newDatasetKey, cfg.release, factory);
    idProvider.run();
    DateUtils.logDuration(LOG, "ID provider", start);
  }

  @Override
  protected void onLogAppenderClose() {
    // set MDC again just to make sure we copy the logs correctly in case some other code has changed the MDC wrongly
    LoggingUtils.setDatasetMDC(datasetKey, attempt, getClass());
    LOG.info(LoggingUtils.COPY_RELEASE_LOGS_MARKER, "Copy release logs for {} {}", getJobName(), getKey());
    super.onLogAppenderClose();
  }

  /**
   * Creates a new DOI for the new release using the latest public release for the prevReleaseKey
   * @return the previous releases datasetKey
   */
  protected Integer createReleaseDOI() throws Exception {
    try (SqlSession session = factory.openSession(true)) {
      // find previous public release needed for DOI management
      final Integer prevReleaseKey = session.getMapper(DatasetMapper.class).previousRelease(newDatasetKey);
      return createReleaseDOI(prevReleaseKey);
    }
  }

  /**
   * Creates a new DOI for the new release
   * @return the previous releases datasetKey
   * @param prevReleaseKey the previous release key to build the metadata with
   */
  protected Integer createReleaseDOI(Integer prevReleaseKey) throws Exception {
    // assign DOIs?
    if (cfg.doi != null) {
      newDataset.setDoi(cfg.doi.datasetDOI(newDatasetKey));
      updateDataset(newDataset);

      LOG.info("Use previous release {} for DOI metadata of {}", prevReleaseKey, newDatasetKey);
      var attr = doiUpdater.buildReleaseMetadata(datasetKey, false, newDataset, prevReleaseKey);
      LOG.info("Creating new DOI {} for release {}", newDataset.getDoi(), newDatasetKey);
      doiService.createSilently(attr);
    }
    return prevReleaseKey;
  }

  /**
   * Looks up a previous DOI and verifies the core metrics have not changed since.
   * Otherwise NULL is returned and a new DOI should be issued.
   * @param prevReleaseKey the datasetKey of the previous release or NULL if never released before
   * @param sourceKey
   */
  private DOI findSourceDOI(Integer prevReleaseKey, int sourceKey, SqlSession session) {
    if (prevReleaseKey != null) {
      DatasetSourceMapper psm = session.getMapper(DatasetSourceMapper.class);
      var prevSrc = psm.getReleaseSource(sourceKey, prevReleaseKey);
      if (prevSrc != null && prevSrc.getDoi() != null && prevSrc.getDoi().isCOL()) {
        // compare basic metrics
        var metrics = srcDao.sourceMetrics(datasetKey, sourceKey);
        var prevMetrics = srcDao.sourceMetrics(prevReleaseKey, sourceKey);
        if (Objects.equals(metrics.getTaxaByRankCount(), prevMetrics.getTaxaByRankCount())
            && Objects.equals(metrics.getSynonymsByRankCount(), prevMetrics.getSynonymsByRankCount())
            && Objects.equals(metrics.getVernacularsByLanguageCount(), prevMetrics.getVernacularsByLanguageCount())
            && Objects.equals(metrics.getUsagesByStatusCount(), prevMetrics.getUsagesByStatusCount())
        ) {
          return prevSrc.getDoi();
        }
      }
    }
    return null;
  }

  @Override
  void finalWork() throws Exception {
    checkIfCancelled();
    // remove orphan sectors and decisions not used in the data, e.g. merge sectors from the XCOL
    try (SqlSession session = factory.openSession(true)) {
      int del = session.getMapper(SectorMapper.class).deleteOrphans(newDatasetKey);
      LOG.info("Removed {} unused sectors", del);
    }

    updateState(ImportState.ARCHIVING);
    LocalDateTime start = LocalDateTime.now();
    try (SqlSession session = factory.openSession(true)) {
      // treat source. Archive dataset metadata & logos & assign a potentially new DOI

      DatasetSourceMapper psm = session.getMapper(DatasetSourceMapper.class);
      var cm = session.getMapper(CitationMapper.class);
      final AtomicInteger counter = new AtomicInteger(0);
      final var issueSourceDOIs = settings.isEnabled(Setting.RELEASE_ISSUE_SOURCE_DOIS);
      for (var d : srcDao.list(datasetKey, newDataset, true, true)) {
        if (issueSourceDOIs && cfg.doi != null) {
          // can we reuse a previous DOI for the source?
          DOI srcDOI = findSourceDOI(prevReleaseKey, d.getKey(), session);
          if (srcDOI == null) {
            srcDOI = cfg.doi.datasetSourceDOI(newDatasetKey, d.getKey());
            d.setDoi(srcDOI);
            LOG.info("Creating new DOI {} for modified source {} of release {}", srcDOI, d.getKey(), newDatasetKey);
            var srcAttr = doiUpdater.buildSourceMetadata(d, newDataset, true);
            doiService.createSilently(srcAttr);
          }
          d.setDoi(srcDOI);
        }

        LOG.info("Archive dataset {}#{} for release {}", d.getKey(), attempt, newDatasetKey);
        psm.create(newDatasetKey, d);
        cm.createRelease(d.getKey(), newDatasetKey, attempt);
        // archive logos
        try {
          imageService.datasetLogoArchived(newDatasetKey, d.getKey());
        } catch (IOException e) {
          LOG.warn("Failed to archive logo for source dataset {} of release {}", d.getKey(), newDatasetKey, e);
        }
        counter.incrementAndGet();
        checkIfCancelled();
      }
      LOG.info("Archived metadata for {} source datasets of release {}", counter.get(), newDatasetKey);
    }
    DateUtils.logDuration(LOG, "Archiving sources", start);

    // aggregate authors for release from sources
    checkIfCancelled();
    var authGen = new AuthorlistGenerator(validator, srcDao);
    if (authGen.appendSourceAuthors(newDataset, settings)) {
      dDao.update(newDataset, user);
    }

    // update both the projects and release datasets import attempt pointer
    checkIfCancelled();
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      dm.updateLastImport(datasetKey, attempt);
      dm.updateLastImport(newDatasetKey, attempt);
    }
    // flush varnish cache for dataset/3LR and LRC (or LXR & LXRC)
    if (client != null && datasetApiBuilder != null) {
      URI api = datasetApiBuilder.build(datasetKey);
      VarnishUtils.ban(client, api);
      VarnishUtils.ban(client, portalURI); // flush also /colseo which also points to latest releases
    }
    // kick off exports
    if (settings.isEnabled(Setting.RELEASE_PREPARE_DOWNLOADS)) {
      LOG.info("Prepare exports for release {}", newDatasetKey);
      for (DataFormat df : EXPORT_FORMATS) {
        ExportRequest req = new ExportRequest();
        req.setDatasetKey(newDatasetKey);
        req.setFormat(df);
        req.setExcel(false);
        req.setExtended(true);
        exportManager.submit(req, user);
      }
    }
  }

  @Override
  protected void onError(Exception e) {
    super.onError(e);
    // remove reports
    File dir = cfg.release.reportDir(datasetKey, attempt);
    if (dir.exists()) {
      LOG.debug("Remove release report {}-{} for failed dataset {}", datasetKey, metrics.attempt(), newDatasetKey);
      FileUtils.deleteQuietly(dir);
    }
  }

  public static class IdPreviewRelease extends ProjectRelease {
    public IdPreviewRelease(SqlSessionFactory factory, NameUsageIndexService indexService, ImageService imageService, DatasetImportDao diDao, DatasetDao dDao, ReferenceDao rDao, NameDao nDao, SectorDao sDao, int datasetKey, int userKey, WsServerConfig cfg, CloseableHttpClient client, ExportManager exportManager, DoiService doiService, DoiUpdater doiUpdater, Validator validator) {
      super(factory, indexService, imageService, diDao, dDao, rDao, nDao, sDao, datasetKey, userKey, cfg, client, exportManager, doiService, doiUpdater, validator);
    }

    @Override
    void prepWork() throws Exception {
      settings.disable(Setting.RELEASE_REMOVE_BARE_NAMES);
      super.prepWork();
      // turn off map ids which will prevent the tables from being deleted on error!
      mapIds = false;
      // now fail to ignore the rest of the release procedure
      throw new RuntimeException("WE ONLY WANT THE PREPARATION STEP TO RUN AND SAVE ID MAP TABLES");
    }
  }
}
