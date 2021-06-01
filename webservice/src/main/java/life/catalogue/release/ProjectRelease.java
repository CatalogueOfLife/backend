package life.catalogue.release;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.*;
import life.catalogue.cache.VarnishUtils;
import life.catalogue.common.text.CitationUtils;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.DatasetProjectSourceDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.ProjectSourceMapper;
import life.catalogue.doi.service.DatasetConverter;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;
import org.apache.commons.io.FileUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import javax.ws.rs.core.UriBuilder;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class ProjectRelease extends AbstractProjectCopy {
  private static final String DEFAULT_TITLE_TEMPLATE = "{title}, {date}";
  private static final String DEFAULT_ALIAS_TEMPLATE = "{aliasOrTitle}-{date}";

  private final ImageService imageService;
  private final WsServerConfig cfg;
  private final UriBuilder datasetApiBuilder;
  private final CloseableHttpClient client;
  private final ExportManager exportManager;
  private final DoiService doiService;
  private final DatasetProjectSourceDao sourceDao;
  private final DatasetConverter converter;

  ProjectRelease(SqlSessionFactory factory, NameUsageIndexService indexService, DatasetImportDao diDao, DatasetDao dDao, ImageService imageService,
                 int datasetKey, int userKey, WsServerConfig cfg, CloseableHttpClient client, ExportManager exportManager,
                 DoiService doiService, DatasetConverter converter) {
    super("releasing", factory, diDao, dDao, indexService, userKey, datasetKey, true);
    this.imageService = imageService;
    this.doiService = doiService;
    this.cfg = cfg;
    this.datasetApiBuilder = cfg.apiURI == null ? null : UriBuilder.fromUri(cfg.apiURI).path("dataset/{key}LR");
    this.client = client;
    this.exportManager = exportManager;
    this.sourceDao = new DatasetProjectSourceDao(factory);
    this.converter = converter;
  }

  @Override
  protected void modifyDataset(Dataset d, DatasetSettings ds) {
    super.modifyDataset(d, ds);
    d.setOrigin(DatasetOrigin.RELEASED);

    final LocalDate today = LocalDate.now();
    d.setIssued(today);
    d.setVersion(today.toString());

    String alias = CitationUtils.fromTemplate(d, ds, Setting.RELEASE_ALIAS_TEMPLATE, DEFAULT_ALIAS_TEMPLATE);
    d.setAlias(alias);

    String title = CitationUtils.fromTemplate(d, ds, Setting.RELEASE_TITLE_TEMPLATE, DEFAULT_TITLE_TEMPLATE);
    d.setTitle(title);

    if (ds != null && ds.has(Setting.RELEASE_CITATION_TEMPLATE)) {
      String citation = CitationUtils.fromTemplate(d, ds.getString(Setting.RELEASE_CITATION_TEMPLATE));
      d.setCitation(citation);
    }

    d.setPrivat(true); // all releases are private candidate releases first
  }

  @Override
  void prepWork() throws Exception {
    // assign DOIs?
    if (cfg.doi != null) {
      newDataset.setDoi(cfg.doi.datasetDOI(newDatasetKey));
      updateDataset(newDataset);
      var attr = converter.release(newDataset, false);
      LOG.info("Creating new DOI {} for release {}", newDataset.getDoi(), newDatasetKey);
      doiService.createSilently(attr);
    }

    // treat source. Archive dataset metadata & logos & assign a potentially new DOI
    updateState(ImportState.ARCHIVING);
    DatasetProjectSourceDao dao = new DatasetProjectSourceDao(factory);
    try (SqlSession session = factory.openSession(true)) {
      // find previous public release needed for DOI management
      final Integer prevReleaseKey = findPreviousRelease(datasetKey, session);
      LOG.info("Last public release was {}", prevReleaseKey);

      ProjectSourceMapper psm = session.getMapper(ProjectSourceMapper.class);
      final AtomicInteger counter = new AtomicInteger(0);
      dao.list(datasetKey, newDataset, true).forEach(d -> {
        if (cfg.doi != null) {
          // can we reuse a previous DOI for the source?
          DOI srcDOI = findSourceDOI(prevReleaseKey, d.getKey(), session);
          if (srcDOI == null) {
            srcDOI = cfg.doi.datasetSourceDOI(newDatasetKey, d.getKey());
            d.setDoi(srcDOI);
            LOG.info("Creating new DOI {} for modified source {} of release {}", srcDOI, d.getKey(), newDatasetKey);
            var srcAttr = converter.source(d, newDataset, true);
            doiService.createSilently(srcAttr);
          }
          d.setDoi(srcDOI);
        }

        LOG.info("Archive dataset {}#{} for release {}", d.getKey(), d.getImportAttempt(), newDatasetKey);
        psm.create(newDatasetKey, d);
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
    IdProvider idProvider = new IdProvider(datasetKey, metrics.getAttempt(), newDatasetKey, cfg.release, factory);
    idProvider.run();
  }

  /**
   * This looks up the previous release by ignoring the latest releases and ignoring the very latest one.
   * Private flags do not matter.
   * @param datasetKey
   * @param session
   */
  static Integer findPreviousRelease(int datasetKey, SqlSession session){
    DatasetMapper dm = session.getMapper(DatasetMapper.class);
    DatasetSearchRequest req = new DatasetSearchRequest();
    req.setPrivat(true);
    req.setReleasedFrom(datasetKey);
    req.setSortBy(DatasetSearchRequest.SortBy.CREATED);

    var releases = dm.search(req, DatasetMapper.MAGIC_ADMIN_USER_KEY, new Page(0, 2));
    return releases.size() < 2 ? null : releases.get(1).getKey();
  }

  /**
   * Looks up a previous DOI and verifies the core metrics have not changed since.
   * Otherwise NULL is returned and a new DOI should be issued.
   * @param prevReleaseKey the datasetKey of the previous release or NULL if never released before
   * @param sourceKey
   */
  private DOI findSourceDOI(Integer prevReleaseKey, int sourceKey, SqlSession session) {
    if (prevReleaseKey != null) {
      ProjectSourceMapper psm = session.getMapper(ProjectSourceMapper.class);
      var prevSrc = psm.getReleaseSource(sourceKey, prevReleaseKey);
      if (prevSrc != null && prevSrc.getDoi() != null) {
        // compare basic metrics
        var metrics = sourceDao.projectSourceMetrics(datasetKey, sourceKey);
        var prevMetrics = sourceDao.projectSourceMetrics(prevReleaseKey, sourceKey);
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
    // update both the projects and release datasets import attempt pointer
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      dm.updateLastImport(datasetKey, metrics.getAttempt());
      dm.updateLastImport(newDatasetKey, metrics.getAttempt());
    }
    // flush varnish cache for dataset/3LR and LRC
    if (client != null && datasetApiBuilder != null) {
      URI api = datasetApiBuilder.build(datasetKey);
      VarnishUtils.ban(client, api);
    }
    // kick off exports
    for (DataFormat df : DataFormat.values()) {
      if (df.isExportable()) {
        ExportRequest req = new ExportRequest();
        req.setDatasetKey(newDatasetKey);
        req.setFormat(df);
        exportManager.submit(req, user);
      }
    }
  }

  @Override
  void onError() {
    // remove reports
    File dir = cfg.release.reportDir(datasetKey, metrics.getAttempt());
    if (dir.exists()) {
      LOG.debug("Remove release report {}-{} for failed dataset {}", datasetKey, metrics.attempt(), newDatasetKey);
      FileUtils.deleteQuietly(dir);
    }
  }
}
