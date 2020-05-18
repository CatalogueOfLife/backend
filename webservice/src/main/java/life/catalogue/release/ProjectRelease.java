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
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.io.IOException;
import java.time.LocalDate;

public class ProjectRelease extends ProjectRunnable {
  private static final String DEFAULT_TITLE_TEMPLATE = "{title}, {date}";

  private final AcExporter exporter;
  @Deprecated
  private final life.catalogue.release.Logger logger = new life.catalogue.release.Logger(LOG);

  ProjectRelease(SqlSessionFactory factory, NameUsageIndexService indexService, AcExporter exporter, DatasetImportDao diDao, int datasetKey, Dataset release, int userKey) {
    super("releasing", factory, diDao, indexService, userKey, datasetKey, release);
    this.exporter = exporter;
  }

  @Override
  void dataWork() throws Exception {
    logger.log("Release catalogue "+ datasetKey +" to new dataset" + getNewDatasetKey());
    // map ids
    updateState(ImportState.MATCHING);
    mapIds();
    // copy data
    updateState(ImportState.INSERTING);
    copyTable(SectorMapper.class, Sector.class, this::updateEntity);
    copyTable(ReferenceMapper.class, Reference.class, this::updateVerbatimEntity);
    copyTable(NameMapper.class, Name.class, this::updateVerbatimEntity);
    copyTable(NameRelationMapper.class, NameRelation.class, this::updateEntity);
    copyTable(TaxonMapper.class, Taxon.class, this::updateVerbatimEntity);
    copyTable(SynonymMapper.class, Synonym.class, this::updateVerbatimEntity);

    copyExtTable(VernacularNameMapper.class, VernacularName.class, this::updateEntity);
    copyExtTable(DistributionMapper.class, Distribution.class, this::updateEntity);
    copyTable(EstimateMapper.class, SpeciesEstimate.class, this::updateEntity);
    // archive dataset metadata
    try (SqlSession session = factory.openSession(false)) {
      DatasetArchiveMapper dam = session.getMapper(DatasetArchiveMapper.class);
      DatasetPatchMapper dpm = session.getMapper(DatasetPatchMapper.class);
      dam.processSources(datasetKey).forEach(d -> {
        DatasetMetadata patch = dpm.get(datasetKey, d.getKey());
        if (patch != null) {
          LOG.debug("Apply dataset patch from project {} to {}: {}", datasetKey, d.getKey(), d.getTitle());
          d.apply(patch);
        }
        d.setDatasetKey(newDatasetKey);
        LOG.debug("Archive dataset {}: {} for release {}", d.getKey(), d.getTitle(), newDatasetKey);
        dam.createProjectSource(d);
      });
    }
  }

  @Override
  void finalWork() throws Exception {
    // ac-export
    updateState(ImportState.EXPORTING);
    export();
  }

  public static void releaseInit(Dataset d, DatasetSettings ds) {
    String tmpl = DEFAULT_TITLE_TEMPLATE;
    if (ds.has(Setting.RELEASE_TITLE_TEMPLATE)) {
      tmpl = ds.getString(Setting.RELEASE_TITLE_TEMPLATE);
    }
    String title = SimpleTemplate.render(tmpl, d);
    d.setTitle(title);
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
    logger.log("Map IDs");
    //TODO: match & generate ids
  }

  private <T extends DatasetScopedEntity<Integer>> void updateEntity(T obj) {
    obj.setId(null);
    obj.setDatasetKey(newDatasetKey);
  }
  private <C extends DSID<String> & VerbatimEntity> void updateVerbatimEntity(C obj) {
    obj.setDatasetKey(newDatasetKey);
    obj.setVerbatimKey(null);
  }
  private void updateEntity(NameRelation obj) {
    obj.setId(null);
    obj.setDatasetKey(newDatasetKey);
    obj.setVerbatimKey(null);
  }
  private <E extends DatasetScopedEntity<Integer> & VerbatimEntity> void updateEntity(TaxonExtension<E> obj) {
    obj.getObj().setId(null);
    obj.getObj().setDatasetKey(newDatasetKey);
    obj.getObj().setVerbatimKey(null);
  }

  public void export() throws IOException {
    try {
      exporter.export(newDatasetKey);
    } catch (Throwable e) {
      LOG.error("Error exporting catalogue {}", newDatasetKey, e);
      logger.log("\n\nERROR!");
      if (e.getMessage() != null) {
        logger.log(e.getMessage());
      }
    }
  }
  
}
