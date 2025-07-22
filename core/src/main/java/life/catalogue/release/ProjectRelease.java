package life.catalogue.release;

import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.model.VerbatimSource;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.Setting;
import life.catalogue.cache.VarnishUtils;
import life.catalogue.common.date.DateUtils;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.common.io.HttpUtils;
import life.catalogue.common.text.CitationUtils;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.common.util.YamlUtils;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.dao.*;
import life.catalogue.db.mapper.*;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.DoiConfig;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import jakarta.validation.Validator;
import jakarta.ws.rs.core.UriBuilder;

import static life.catalogue.api.util.ObjectUtils.coalesce;
import static life.catalogue.api.util.ObjectUtils.firstNonEmptyList;

public class ProjectRelease extends AbstractProjectCopy {
  private static final Logger LOG = LoggerFactory.getLogger(ProjectRelease.class);
  public static Set<DataFormat> EXPORT_FORMATS = Set.of(DataFormat.TEXT_TREE, DataFormat.COLDP, DataFormat.DWCA);
  private static final String DEFAULT_ALIAS_TEMPLATE = "{aliasOrTitle}-{date}";
  private static final String DEFAULT_VERSION_TEMPLATE = "{date}";

  protected final ReleaseConfig cfg;
  protected final DoiConfig doiCfg;
  protected final URI apiURI;
  protected final URI clbURI;
  protected final ReferenceDao rDao;
  protected final NameDao nDao;
  protected final SectorDao sDao;
  protected final VerbatimDao vDao;
  private final UriBuilder datasetApiBuilder;
  private final URI portalURI;
  private final CloseableHttpClient client;
  private final ImageService imageService;
  private final ExportManager exportManager;
  private final DoiService doiService;
  private final DoiUpdater doiUpdater;
  private Integer prevReleaseKey;
  protected ProjectReleaseConfig prCfg;

  ProjectRelease(SqlSessionFactory factory, NameUsageIndexService indexService, ImageService imageService,
                 DatasetImportDao diDao, DatasetDao dDao, ReferenceDao rDao, NameDao nDao, SectorDao sDao,
                 int datasetKey, int userKey, ReleaseConfig cfg, DoiConfig doiCfg, URI apiURI, URI clbURI,
                 CloseableHttpClient client, ExportManager exportManager,
                 DoiService doiService, DoiUpdater doiUpdater, Validator validator) {
    this("releasing", factory, indexService, imageService, diDao, dDao, rDao, nDao, sDao, datasetKey, userKey,
      cfg, doiCfg, apiURI, clbURI, client, exportManager, doiService, doiUpdater, validator);
  }

  ProjectRelease(String action, SqlSessionFactory factory, NameUsageIndexService indexService, ImageService imageService,
                 DatasetImportDao diDao, DatasetDao dDao, ReferenceDao rDao, NameDao nDao, SectorDao sDao,
                 int baseReleaseOrProjectKey, int userKey, ReleaseConfig cfg, DoiConfig doiCfg, URI apiURI, URI clbURI,
                 CloseableHttpClient client, ExportManager exportManager,
                 DoiService doiService, DoiUpdater doiUpdater, Validator validator) {
    super(action, factory, diDao, dDao, indexService, validator, userKey, baseReleaseOrProjectKey, true, cfg.deleteOnError);
    this.cfg = cfg;
    this.doiCfg = doiCfg;
    this.apiURI = apiURI;
    this.clbURI = clbURI;
    this.imageService = imageService;
    this.doiService = doiService;
    this.rDao = rDao;
    this.nDao = nDao;
    this.sDao = sDao;
    String latestRelease = String.format("L%sR", getClass().equals(XRelease.class) ? "X" : "");
    this.datasetApiBuilder = apiURI == null ? null : UriBuilder.fromUri(apiURI).path("dataset/{key}"+latestRelease);
    this.portalURI = apiURI == null ? null : UriBuilder.fromUri(apiURI).path("portal").build();
    this.client = client;
    this.exportManager = exportManager;
    this.doiUpdater = doiUpdater;
    this.vDao = new VerbatimDao(factory);
  }

  @Override
  void initJob() throws Exception {
    super.initJob();
    // point to release in CLB - this requires the datasetKey to exist already
    newDataset.setUrl(UriBuilder.fromUri(clbURI)
      .path("dataset")
      .path(newDataset.getKey().toString())
      .build());
    dDao.update(newDataset, user);
  }

  @Override
  protected void loadConfigs() {
    prCfg = loadConfig(ProjectReleaseConfig.class, settings.getURI(Setting.RELEASE_CONFIG), true);
    verifyConfigTemplates();
  }

  @VisibleForTesting
  public void setPrCfg(ProjectReleaseConfig prCfg) {
    this.prCfg = prCfg;
  }

  /**
   * Verifies settings values, in particular the freemarker citation templates
   */
  protected void verifyConfigTemplates() throws IllegalArgumentException {
    Dataset d = new Dataset();
    d.setKey(1);
    d.setAlias("alias");
    d.setTitle("title");
    d.setOrigin(DatasetOrigin.PROJECT);
    d.setIssued(FuzzyDate.now());
    d.setLogo(URI.create("https://gbif.org"));
    d.setUrl(d.getLogo());
    d.setCreated(LocalDateTime.now());
    d.setModified(LocalDateTime.now());
    d.setImported(LocalDateTime.now());

    var dw = metadataTemplateData(new CitationUtils.ReleaseWrapper(d,d,d));

    // verify all templates and throw early otherwise
    verifyTemplate("alias", prCfg.metadata.alias, dw);
    verifyTemplate("title", prCfg.metadata.title, dw);
    verifyTemplate("version", prCfg.metadata.version, dw);
    verifyTemplate("description", prCfg.metadata.description, dw);
  }

  /**
   * Override to allow more complex data wrappers in subclasses to be used in metadata templates
   * @param data template data as created from the regular ProjectRelease
   * @return the same or new instance to be used in generating metadata
   */
  protected CitationUtils.ReleaseWrapper metadataTemplateData(CitationUtils.ReleaseWrapper data) {
    return data;
  }

  private void verifyTemplate(String field, String template, CitationUtils.ReleaseWrapper d) throws IllegalArgumentException {
    if (template != null) {
      try {
        CitationUtils.fromTemplate(d, template);
      } catch (RuntimeException e) {
        throw new IllegalArgumentException("Bad template for " + field + ": " + e.getMessage(), e);
      }
    }
  }

  @VisibleForTesting
  public static <T> T loadConfig(Class<T> configClass, URI url, boolean logContent) {
    if (url == null) {
      LOG.warn("No {} config supplied, use defaults", configClass.getSimpleName());
      try {
        return configClass.getDeclaredConstructor().newInstance();
      } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        throw new RuntimeException(e);
      }

    } else {
      try {
        var http = new HttpUtils();
        String yaml = http.get(url);
        if (logContent) {
          System.out.println("yaml content found under " + url);
          System.out.println("----------");
          System.out.println(yaml);
          System.out.println("----------");
        }
        return YamlUtils.readString(configClass, yaml);
      } catch (IOException | InterruptedException e) {
        throw new IllegalArgumentException("Invalid release configuration at "+ url, e);
      }
    }
  }

  @Override
  public String getEmailTemplatePrefix() {
    return "release";
  }

  public URI getReportURI() {
    return cfg.reportURI(projectKey, attempt);
  }

  @Override
  protected void modifyDataset(Dataset d) {
    super.modifyDataset(d);
    d.setOrigin(DatasetOrigin.RELEASE);

    final FuzzyDate today = FuzzyDate.now();
    d.setIssued(today);

    var data = metadataTemplateData(new CitationUtils.ReleaseWrapper(d, base, dataset));
    d.setAlias( CitationUtils.fromTemplate(data, coalesce(prCfg.metadata.alias, DEFAULT_ALIAS_TEMPLATE)) );
    d.setTitle( CitationUtils.fromTemplate(data, coalesce(prCfg.metadata.title, d.getTitle())) );
    d.setVersion( CitationUtils.fromTemplate(data, coalesce(prCfg.metadata.version, DEFAULT_VERSION_TEMPLATE)) );
    d.setDescription( CitationUtils.fromTemplate(data, coalesce(prCfg.metadata.description, d.getDescription())) );
    d.setKeyword( firstNonEmptyList(prCfg.metadata.keyword, d.getKeyword()) );

    d.setContact( coalesce(prCfg.metadata.contact, d.getContact()) );
    d.setPublisher( coalesce(prCfg.metadata.publisher, d.getPublisher()) );
    d.setCreator( firstNonEmptyList(prCfg.metadata.creator, d.getCreator()) );
    d.setEditor( firstNonEmptyList(prCfg.metadata.editor, d.getEditor()) );
    d.setContributor( firstNonEmptyList(prCfg.metadata.contributor, d.getContributor()) );

    d.setConversion( coalesce(prCfg.metadata.conversion, d.getConversion()) );
    d.setConfidence( coalesce(prCfg.metadata.confidence, d.getConfidence()) );
    d.setCompleteness( coalesce(prCfg.metadata.completeness, d.getCompleteness()) );
    d.setGeographicScope( coalesce(prCfg.metadata.geographicScope, d.getGeographicScope()) );
    d.setTaxonomicScope( coalesce(prCfg.metadata.taxonomicScope, d.getTaxonomicScope()) );
    d.setTemporalScope( coalesce(prCfg.metadata.temporalScope, d.getTemporalScope()) );

    // all releases are private candidate releases first
    d.setPrivat(true);
  }

  /**
   * @param dKey datasetKey to remove name & reference orphans from.
   */
  void removeOrphans(int dKey) throws InterruptedException {
    checkIfCancelled();
    final LocalDateTime start = LocalDateTime.now();
    nDao.deleteOrphans(dKey, null, user);
    rDao.deleteOrphans(dKey, null, user);
    vDao.deleteOrphans(dKey, user);
    DateUtils.logDuration(LOG, "Removing orphans", start);
  }

  @Override
  void prepWork() throws Exception {
    super.prepWork();

    LocalDateTime start;

    if (prCfg.removeBareNames) {
      start = LocalDateTime.now();
      removeOrphans(projectKey);
      DateUtils.logDuration(LOG, "Remove orphans", start);
    }

    prevReleaseKey = createReleaseDOI();

    // map ids
    start = LocalDateTime.now();
    updateState(ImportState.MATCHING);
    IdProvider idp = new IdProvider(projectKey, projectKey, DatasetOrigin.RELEASE, attempt, newDatasetKey, cfg, factory);
    idp.mapIds();
    idp.report();
    DateUtils.logDuration(LOG, "ID provider", start);
  }

  @Override
  protected void onLogAppenderClose() {
    // set MDC again just to make sure we copy the logs correctly in case some other code has changed the MDC wrongly
    LoggingUtils.setDatasetMDC(projectKey, attempt, getClass());
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
    checkIfCancelled();
    // assign DOIs?
    if (doiCfg != null) {
      newDataset.setDoi(doiCfg.datasetDOI(newDatasetKey));
      updateDataset(newDataset);

      LOG.info("Use previous release {} for DOI metadata of {}", prevReleaseKey, newDatasetKey);
      var attr = doiUpdater.buildReleaseMetadata(projectKey, false, newDataset, prevReleaseKey);
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
        var metrics = srcDao.sourceMetrics(projectKey, sourceKey, null);
        var prevMetrics = srcDao.sourceMetrics(prevReleaseKey, sourceKey, null);
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
      LOG.info("Removed {} sectors without data in release {}", del, newDatasetKey);
    }

    updateState(ImportState.ARCHIVING);
    LocalDateTime start = LocalDateTime.now();
    try (SqlSession session = factory.openSession(true)) {
      final AtomicInteger counter = new AtomicInteger(0);
      // treat source. Archive dataset metadata & logos & assign a potentially new DOI
      DatasetSourceMapper psm = session.getMapper(DatasetSourceMapper.class);
      var cm = session.getMapper(CitationMapper.class);
      // create fixed source dataset records for this release.
      // This DOES create source dataset records for aggregated publishers.
      // It does not create source records for merge sectors without data in the release itself!
      for (var d : srcDao.listSectorBasedSources(projectKey, newDatasetKey, true)) {
        if (prCfg.issueSourceDOIs && doiCfg != null) {
          // can we reuse a previous DOI for the source?
          DOI srcDOI = findSourceDOI(prevReleaseKey, d.getKey(), session);
          if (srcDOI == null) {
            srcDOI = doiCfg.datasetSourceDOI(newDatasetKey, d.getKey());
            d.setDoi(srcDOI);
            LOG.info("Creating new DOI {} for modified source {} of release {}", srcDOI, d.getKey(), newDatasetKey);
            var srcAttr = doiUpdater.buildSourceMetadata(d, newDataset, true);
            doiService.createSilently(srcAttr);
          }
          d.setDoi(srcDOI);
        }

        LOG.info("Archive dataset {}#{} for release {}: {}", d.getKey(), attempt, newDatasetKey, d.getAliasOrTitle());
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
    if (authGen.appendSourceAuthors(newDataset, prCfg.metadata)) {
      dDao.update(newDataset, user);
      if (newDataset.getDoi() != null) {
        var attr = doiUpdater.buildReleaseMetadata(projectKey, false, newDataset, prevReleaseKey);
        LOG.info("Updating DOI release metadata {}", newDataset.getDoi());
        doiService.updateSilently(attr);
      }
    }

    // update both the projects and release datasets import attempt pointer
    checkIfCancelled();
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      dm.updateLastImport(projectKey, attempt);
      dm.updateLastImport(newDatasetKey, attempt);
    }
    // flush varnish cache for dataset/3LR and LRC (or LXR & LXRC)
    if (client != null && datasetApiBuilder != null) {
      URI api = datasetApiBuilder.build(projectKey);
      VarnishUtils.ban(client, api);
      VarnishUtils.ban(client, portalURI); // flush also /colseo which also points to latest releases
    }
    // kick off exports
    if (prCfg.prepareDownloads) {
      LOG.info("Prepare exports for release {}", newDatasetKey);
      for (DataFormat df : EXPORT_FORMATS) {
        ExportRequest req = new ExportRequest();
        req.setDatasetKey(newDatasetKey);
        req.setFormat(df);
        req.setExcel(false);
        req.setExtended(df != DataFormat.TEXT_TREE);
        exportManager.submit(req, user);
      }
    }
    // generic hooks
    if (cfg.actions != null && cfg.actions.containsKey(projectKey)) {
      // reload dataset metadata
      final Dataset d;
      try (SqlSession session = factory.openSession(true)) {
        d = session.getMapper(DatasetMapper.class).get(newDatasetKey);
      }
      for (var action : cfg.actions.get(projectKey)) {
        if (!action.onPublish) {
          action.call(client, d);
        }
      }
    }
  }

  @Override
  protected void onError(Exception e) {
    super.onError(e);
    // remove reports
    File dir = cfg.reportDir(projectKey, attempt);
    if (dir.exists()) {
      LOG.info("Remove release report {}-{} for failed dataset {}", projectKey, metrics.attempt(), newDatasetKey);
      FileUtils.deleteQuietly(dir);
    }
  }

}
