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
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.common.text.CitationUtils;
import life.catalogue.dao.*;
import life.catalogue.db.mapper.CitationMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetSourceMapper;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.validation.Validator;
import javax.ws.rs.core.UriBuilder;

import org.apache.commons.io.FileUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

public class ProjectRelease extends AbstractProjectCopy {
  public static Set<DataFormat> EXPORT_FORMATS = Set.of(DataFormat.TEXT_TREE, DataFormat.COLDP, DataFormat.DWCA, DataFormat.ACEF);
  private static final String DEFAULT_ALIAS_TEMPLATE = "{aliasOrTitle}-{date}";
  private static final String DEFAULT_VERSION_TEMPLATE = "{date}";

  protected final WsServerConfig cfg;
  protected final NameDao nDao;
  protected final SectorDao sDao;
  private final UriBuilder datasetApiBuilder;
  private final URI portalURI;
  private final CloseableHttpClient client;
  private final ImageService imageService;
  private final ExportManager exportManager;
  private final DoiService doiService;
  private final DoiUpdater doiUpdater;

  ProjectRelease(SqlSessionFactory factory, NameUsageIndexService indexService, DatasetImportDao diDao, DatasetDao dDao, NameDao nDao, SectorDao sDao,
                 ImageService imageService,
                 int datasetKey, int userKey, WsServerConfig cfg, CloseableHttpClient client, ExportManager exportManager,
                 DoiService doiService, DoiUpdater doiUpdater, Validator validator) {
    this("releasing", factory, indexService, diDao, dDao, nDao, sDao, imageService, datasetKey, userKey, cfg, client, exportManager, doiService, doiUpdater, validator);
  }

  ProjectRelease(String action, SqlSessionFactory factory, NameUsageIndexService indexService, DatasetImportDao diDao, DatasetDao dDao, NameDao nDao, SectorDao sDao,
                 ImageService imageService,
                 int datasetKey, int userKey, WsServerConfig cfg, CloseableHttpClient client, ExportManager exportManager,
                 DoiService doiService, DoiUpdater doiUpdater, Validator validator) {
    super(action, factory, diDao, dDao, indexService, validator, userKey, datasetKey, true);
    this.imageService = imageService;
    this.doiService = doiService;
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
    modifyDataset(datasetKey, d, ds, srcDao, new AuthorlistGenerator(validator));
  }

  public static void modifyDataset(int datasetKey, Dataset d, DatasetSettings ds, DatasetSourceDao srcDao, AuthorlistGenerator authGen) {
    d.setOrigin(DatasetOrigin.RELEASE);

    final FuzzyDate today = FuzzyDate.now();
    d.setIssued(today);

    String version = CitationUtils.fromTemplate(d, ds, Setting.RELEASE_VERSION_TEMPLATE, DEFAULT_VERSION_TEMPLATE);
    d.setVersion(version);

    String alias = CitationUtils.fromTemplate(d, ds, Setting.RELEASE_ALIAS_TEMPLATE, DEFAULT_ALIAS_TEMPLATE);
    d.setAlias(alias);

    // append authors for release?
    authGen.appendSourceAuthors(d, srcDao.list(datasetKey, null, false), ds);

    // all releases are private candidate releases first
    d.setPrivat(true);
  }

  @Override
  void prepWork() throws Exception {
    if (settings.isEnabled(Setting.RELEASE_REMOVE_BARE_NAMES)) {
      LOG.info("Remove bare names from project {}", datasetKey);
      int num = nDao.deleteOrphans(datasetKey, null, user);
      LOG.info("Removed {} bare names from project {}", num, datasetKey);
    }

    final Integer prevReleaseKey = createReleaseDOI();

    try (SqlSession session = factory.openSession(true)) {
      // treat source. Archive dataset metadata & logos & assign a potentially new DOI
      updateState(ImportState.ARCHIVING);
      DatasetSourceDao dao = new DatasetSourceDao(factory);

      DatasetSourceMapper psm = session.getMapper(DatasetSourceMapper.class);
      var cm = session.getMapper(CitationMapper.class);
      final AtomicInteger counter = new AtomicInteger(0);
      dao.list(datasetKey, newDataset, true).forEach(d -> {
        if (cfg.doi != null) {
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
          imageService.archiveDatasetLogo(newDatasetKey, d.getKey());
        } catch (IOException e) {
          LOG.warn("Failed to archive logo for source dataset {} of release {}", d.getKey(), newDatasetKey, e);
        }
        counter.incrementAndGet();
      });
      LOG.info("Archived metadata for {} source datasets of release {}", counter.get(), newDatasetKey);
    }

    // map ids
    updateState(ImportState.MATCHING);
    IdProvider idProvider = new IdProvider(datasetKey, attempt, newDatasetKey, cfg.release, factory);
    idProvider.run();
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
      if (prevSrc != null && prevSrc.getDoi() != null) {
        // compare basic metrics
        var metrics = srcDao.projectSourceMetrics(datasetKey, sourceKey);
        var prevMetrics = srcDao.projectSourceMetrics(prevReleaseKey, sourceKey);
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
    // update both the projects and release datasets import attempt pointer
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
        exportManager.submit(req, user);
      }
    }
  }

  @Override
  void onError() {
    // remove reports
    File dir = cfg.release.reportDir(datasetKey, attempt);
    if (dir.exists()) {
      LOG.debug("Remove release report {}-{} for failed dataset {}", datasetKey, metrics.attempt(), newDatasetKey);
      FileUtils.deleteQuietly(dir);
    }
  }
}
