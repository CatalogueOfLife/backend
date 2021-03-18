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
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.ProjectSourceMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.img.ImageService;
import life.catalogue.matching.NameIndex;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

public class ProjectRelease extends AbstractProjectCopy {
  private static final String DEFAULT_TITLE_TEMPLATE = "{title}, {date}";
  private static final String DEFAULT_ALIAS_TEMPLATE = "{aliasOrTitle}-{date}";

  private final ImageService imageService;
  private final ReleaseConfig cfg;
  private final NameIndex nameIndex;
  private final Dataset release;

  ProjectRelease(SqlSessionFactory factory, NameIndex nameIndex, NameUsageIndexService indexService, DatasetImportDao diDao, DatasetDao dDao, ImageService imageService,
                 int datasetKey, Dataset release, int userKey, ReleaseConfig cfg) {
    super("releasing", factory, diDao, dDao, indexService, userKey, datasetKey, release, true);
    this.imageService = imageService;
    this.nameIndex = nameIndex;
    this.release = release;
    this.cfg = cfg;
    // we update the dataset metadata again as we only now have access to the release attempt and some citation templates might be using this!
    try (SqlSession session = factory.openSession(true)) {
      releaseDataset(release, settings);
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      try {
        dm.update(release);
      } catch (PersistenceException e) {
        if (PgUtils.isUniqueConstraint(e)) {
          // make sure alias is unique - will fail otherwise
          release.setAlias(null);
          dm.create(release);
        } else {
          throw e;
        }
      }
    }
  }

  @Override
  void prepWork() throws Exception {
    // archive dataset metadata & logos
    updateState(ImportState.ARCHIVING);
    DatasetProjectSourceDao dao = new DatasetProjectSourceDao(factory);
    try (SqlSession session = factory.openSession(true)) {
      ProjectSourceMapper psm = session.getMapper(ProjectSourceMapper.class);
      final AtomicInteger counter = new AtomicInteger(0);
      dao.list(datasetKey, release, false).forEach(d -> {
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
    IdProvider idProvider = new IdProvider(datasetKey, metrics.getAttempt(), cfg, factory);
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
  }

  public static void releaseDataset(Dataset d, DatasetSettings ds) {
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

}
