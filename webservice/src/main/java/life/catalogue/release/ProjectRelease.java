package life.catalogue.release;

import com.google.common.annotations.VisibleForTesting;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetMetadata;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.Setting;
import life.catalogue.common.text.SimpleTemplate;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.img.ImageService;
import life.catalogue.matching.NameIndex;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

public class ProjectRelease extends AbstractProjectCopy {
  private static final String DEFAULT_TITLE_TEMPLATE = "{title}, {date}";
  private static final String DEFAULT_ALIAS_TEMPLATE = "{aliasOrTitle}-{date}";
  private static final String DEFAULT_CITATION_TEMPLATE = "{citation} released on {date}";

  private final ImageService imageService;
  private final NameIndex nameIndex;

  ProjectRelease(SqlSessionFactory factory, NameIndex nameIndex, NameUsageIndexService indexService, DatasetImportDao diDao, ImageService imageService,
                 int datasetKey, Dataset release, int userKey) {
    super("releasing", factory, diDao, indexService, userKey, datasetKey, release, true);
    this.imageService = imageService;
    this.nameIndex = nameIndex;
  }

  @Override
  void prepWork() throws Exception {
    // map ids
    updateState(ImportState.MATCHING);
    matchUnmatchedNames();
    new StableIdProvider(datasetKey, metrics.getAttempt(), factory).run();

    // archive dataset metadata & logos
    updateState(ImportState.ARCHIVING);
    try (SqlSession session = factory.openSession(true)) {
      ProjectSourceMapper psm = session.getMapper(ProjectSourceMapper.class);
      DatasetPatchMapper dpm = session.getMapper(DatasetPatchMapper.class);
      final AtomicInteger counter = new AtomicInteger(0);
      psm.processDataset(datasetKey).forEach(d -> {
        DatasetMetadata patch = dpm.get(datasetKey, d.getKey());
        if (patch != null) {
          LOG.debug("Apply dataset patch from project {} to {}: {}", datasetKey, d.getKey(), d.getTitle());
          d.apply(patch);
        }
        d.setDatasetKey(newDatasetKey);
        LOG.debug("Archive dataset {}: {} for release {}", d.getKey(), d.getTitle(), newDatasetKey);
        psm.create(d);
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

    // create new dataset "import" metrics in mother project
    updateState(ImportState.ANALYZING);
    metrics();
  }

  /**
   * Makes sure all names are matched to the names index.
   * When syncing names from other sources the names index match is carried over
   * so there should really not be any name without a match.
   * We still make sure here that at least there is no such case in releases.
   */
  private void matchUnmatchedNames() {
    try (SqlSession session = factory.openSession(false)) {
      AtomicInteger counter = new AtomicInteger();
      NameMapper nm = session.getMapper(NameMapper.class);
      nm.processUnmatched(datasetKey).forEach(n -> {
        if (n.getNameIndexMatchType() == null || n.getNameIndexMatchType() == MatchType.NONE || n.getNameIndexIds().isEmpty()) {
          NameMatch match = nameIndex.match(n, true, false);
          nm.updateMatch(datasetKey, n.getId(), match.getNameIds(), match.getType());
          if (counter.getAndIncrement() % 1000 == 0) {
            session.commit();
          }
        }
      });
      session.commit();
    }
  }

  private void metrics() {
    LOG.info("Build import metrics for dataset " + datasetKey);
    diDao.updateMetrics(metrics, newDatasetKey);
    diDao.update(metrics);

    // update both the projects and release datasets import attempt pointer
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      dm.updateLastImport(datasetKey, metrics.getAttempt());
      dm.updateLastImport(newDatasetKey, metrics.getAttempt());
    }
  }

  static String procTemplate(Dataset d, DatasetSettings ds, Setting setting, String defaultTemplate){
    String tmpl = defaultTemplate;
    if (ds.has(setting)) {
      tmpl = ds.getString(setting);
    }
    if (tmpl != null) {
      return SimpleTemplate.render(tmpl, d);
    }
    return null;
  }

  public static void releaseDataset(Dataset d, DatasetSettings ds) {
    String alias = procTemplate(d, ds, Setting.RELEASE_ALIAS_TEMPLATE, DEFAULT_ALIAS_TEMPLATE);
    d.setAlias(alias);

    String title = procTemplate(d, ds, Setting.RELEASE_TITLE_TEMPLATE, DEFAULT_TITLE_TEMPLATE);
    d.setTitle(title);

    String citation = procTemplate(d, ds, Setting.RELEASE_CITATION_TEMPLATE, DEFAULT_CITATION_TEMPLATE);
    d.setCitation(citation);

    d.setOrigin(DatasetOrigin.RELEASED);
    final LocalDate today = LocalDate.now();
    d.setReleased(today);
    d.setPrivat(true); // all releases are private candidate releases first
    d.setVersion(today.toString());
    d.setCitation(buildCitation(d));
  }

  @VisibleForTesting
  protected static String buildCitation(Dataset d){
    // ${d.authorsAndEditors?join(", ")}, eds. (${d.released.format('yyyy')}). ${d.title}, ${d.released.format('yyyy-MM-dd')}. Digital resource at www.catalogueoflife.org/col. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-8858.
    StringBuilder sb = new StringBuilder();
    for (String au : d.getAuthorsAndEditors()) {
      if (sb.length() > 1) {
        sb.append(", ");
      }
      sb.append(au);
    }
    sb.append(" (")
      .append(d.getReleased().getYear())
      .append("). ")
      .append(d.getTitle())
      .append(", ")
      .append(d.getReleased().toString())
      .append(". Digital resource at www.catalogueoflife.org/col. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-8858.");
    return sb.toString();
  }
}
