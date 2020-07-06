package life.catalogue.release;

import com.google.common.annotations.VisibleForTesting;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.Setting;
import life.catalogue.common.text.SimpleTemplate;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.img.ImageService;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

public class ProjectRelease extends ProjectRunnable {
  private static final String DEFAULT_TITLE_TEMPLATE = "{title}, {date}";
  private static final String DEFAULT_ALIAS_TEMPLATE = "{alias}-{date}";
  private static final String DEFAULT_CITATION_TEMPLATE = "{citation} released on {date}";

  private final AcExporter exporter;
  private final ImageService imageService;

  ProjectRelease(SqlSessionFactory factory, NameUsageIndexService indexService, AcExporter exporter, DatasetImportDao diDao, ImageService imageService,
                 int datasetKey, Dataset release, int userKey) {
    super("releasing", factory, diDao, indexService, userKey, datasetKey, release);
    this.exporter = exporter;
    this.imageService = imageService;
  }

  @Override
  void prepWork() throws Exception {
    // map ids
    updateState(ImportState.MATCHING);
    mapIds();
    // archive dataset metadata & logos
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
          LOG.warn("Failed to archive logo for source dataset {} of release {}", d.getKey(), newDatasetKey);
        }
        counter.incrementAndGet();
      });
      LOG.info("Archived metadata for {} source datasets of release {}", counter.get(), newDatasetKey);
    }
  }

  @Override
  void finalWork() throws Exception {
    // ac-export
    updateState(ImportState.EXPORTING);
    export();
  }

  private static String procTemplate(Dataset d, DatasetSettings ds, Setting setting, String defaultTemplate){
    String tmpl = defaultTemplate;
    if (ds.has(setting)) {
      tmpl = ds.getString(setting);
    }
    return SimpleTemplate.render(tmpl, d);
  }

  public static void releaseInit(Dataset d, DatasetSettings ds) {
    String title = procTemplate(d, ds, Setting.RELEASE_TITLE_TEMPLATE, DEFAULT_TITLE_TEMPLATE);
    d.setTitle(title);

    String alias = procTemplate(d, ds, Setting.RELEASE_ALIAS_TEMPLATE, DEFAULT_ALIAS_TEMPLATE);
    d.setAlias(alias);

    String citation = procTemplate(d, ds, Setting.RELEASE_CITATION_TEMPLATE, DEFAULT_CITATION_TEMPLATE);
    d.setCitation(citation);

    d.setOrigin(DatasetOrigin.RELEASED);
    final LocalDate today = LocalDate.now();
    d.setReleased(today);
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

  private void mapIds() {
    LOG.info("Map IDs");
    //TODO: match & generate ids
  }

  public void export() throws IOException {
    try {
      exporter.export(newDatasetKey);
    } catch (Throwable e) {
      LOG.error("Error exporting release {}", newDatasetKey, e);
    }
  }
  
}
