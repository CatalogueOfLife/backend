package life.catalogue.release;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.Setting;
import life.catalogue.common.text.CitationUtils;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.DatasetProjectSourceDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.ProjectSourceMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.img.ImageService;
import life.catalogue.util.VarnishUtils;
import org.apache.commons.io.FileUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import javax.ws.rs.core.UriBuilder;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

public class ProjectRelease extends AbstractProjectCopy {
  private static final String DEFAULT_TITLE_TEMPLATE = "{title}, {date}";
  private static final String DEFAULT_ALIAS_TEMPLATE = "{aliasOrTitle}-{date}";

  private final ImageService imageService;
  private final ReleaseConfig cfg;
  private final UriBuilder datasetApiBuilder;
  private final CloseableHttpClient client;

  ProjectRelease(SqlSessionFactory factory, NameUsageIndexService indexService, DatasetImportDao diDao, DatasetDao dDao, ImageService imageService,
                 int datasetKey, int userKey, ReleaseConfig cfg, URI api, CloseableHttpClient client) {
    super("releasing", factory, diDao, dDao, indexService, userKey, datasetKey, true);
    this.imageService = imageService;
    this.cfg = cfg;
    this.datasetApiBuilder = api == null ? null : UriBuilder.fromUri(api).path("dataset/{key}LR");
    this.client = client;
  }

  @Override
  protected void modifyDataset(Dataset d, DatasetSettings ds) {
    super.modifyDataset(d, ds);
    d.setOrigin(DatasetOrigin.RELEASED);

    final LocalDate today = LocalDate.now();
    d.setReleased(today);
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
    // archive dataset metadata & logos
    updateState(ImportState.ARCHIVING);
    DatasetProjectSourceDao dao = new DatasetProjectSourceDao(factory);
    try (SqlSession session = factory.openSession(true)) {
      ProjectSourceMapper psm = session.getMapper(ProjectSourceMapper.class);
      final AtomicInteger counter = new AtomicInteger(0);
      dao.list(datasetKey, newDataset, false).forEach(d -> {
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
    IdProvider idProvider = new IdProvider(datasetKey, metrics.getAttempt(), newDatasetKey, cfg, factory);
    idProvider.run();
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
  }

  @Override
  void onError() {
    // remove reports
    File dir = cfg.reportDir(datasetKey, metrics.getAttempt());
    if (dir.exists()) {
      LOG.debug("Remove release report {}-{} for failed dataset {}", datasetKey, metrics.attempt(), newDatasetKey);
      FileUtils.deleteQuietly(dir);
    }
  }
}
