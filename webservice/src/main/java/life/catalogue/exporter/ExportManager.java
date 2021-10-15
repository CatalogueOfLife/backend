package life.catalogue.exporter;

import com.google.common.annotations.VisibleForTesting;
import life.catalogue.WsServerConfig;
import life.catalogue.api.datapackage.ColdpTerm;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DatasetExport;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.dao.DatasetExportDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.img.ImageService;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.simplejavamail.api.mailer.Mailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

public class ExportManager {
  private static final Logger LOG = LoggerFactory.getLogger(ExportManager.class);

  private final WsServerConfig cfg;
  private final SqlSessionFactory factory;
  private final ImageService imageService;
  private final JobExecutor executor;
  private final EmailNotification emailer;
  private final DatasetExportDao dao;
  private final DatasetImportDao diDao;

  public ExportManager(WsServerConfig cfg, SqlSessionFactory factory, JobExecutor executor, ImageService imageService, Mailer mailer, DatasetExportDao exportDao, DatasetImportDao diDao) {
    this.cfg = cfg;
    this.factory = factory;
    this.executor = executor;
    this.imageService = imageService;
    // mailer
    this.emailer = new EmailNotification(mailer, factory, cfg);
    dao = exportDao;
    this.diDao = diDao;
  }

  /**
   * Checks whether an export for the given request already exists and returns the key of the latest export
   * or null if there is no exiting export.
   */
  public DatasetExport exists(ExportRequest req) {
    DatasetExport prev = dao.current(req);
    if (prev != null) {
      LOG.info("Existing {} export {} found for request {}", prev.getRequest().getFormat(), prev.getKey(), req);
      return prev;
    }
    return null;
  }

  public UUID submit(ExportRequest req, int userKey) throws IllegalArgumentException {
    DatasetExport prev = exists(req);
    if (prev != null && !req.isForce()) {
      return prev.getKey();
    }
    validate(req);
    DatasetExporter job;
    switch (req.getFormat()) {
      case COLDP:
        job = new ColdpExporter(req, userKey, factory, cfg, imageService);
        break;
      case DWCA:
        job = new DwcaExporter(req, userKey, factory, cfg, imageService);
        break;
      case ACEF:
        job = new AcefExporter(req, userKey, factory, cfg, imageService);
        break;
      case TEXT_TREE:
        job = new TextTreeExporter(req, userKey, factory, cfg, imageService);
        break;
      case NEWICK:
        job = new NewickExporter(req, userKey, factory, cfg, imageService);
        break;
      case DOT:
        job = new DotExporter(req, userKey, factory, cfg, imageService);
        break;

      default:
        throw new IllegalArgumentException("Export format "+req.getFormat() + " is not supported yet");
    }
    job.setEmailer(emailer);
    return submit(job);
  }

  @VisibleForTesting
  UUID submit(DatasetBlockingJob job) throws IllegalArgumentException {
    executor.submit(job);
    return job.getKey();
  }

  /**
   * Makes sure taxonID exists if given and check number of records for full excel downlaods
   */
  private void validate(ExportRequest req) throws IllegalArgumentException {
    if (req.getTaxonID() != null) {
      try (SqlSession session = factory.openSession()) {
        var root = session.getMapper(NameUsageMapper.class).getSimple(DSID.of(req.getDatasetKey(), req.getTaxonID()));
        if (root == null) {
          throw new IllegalArgumentException("Root taxon " + req.getTaxonID() + " does not exist in dataset " + req.getDatasetKey());
        } else if (!root.getStatus().isTaxon()) {
          throw new IllegalArgumentException("Root usage " + req.getTaxonID() + " is not an accepted taxon but " + root.getStatus());
        }
      }
    }

    if (req.isExcel() && req.getTaxonID() == null && req.getMinRank() == null) {
      // check metrics avoiding truncation early
      var imp = diDao.getLast(req.getDatasetKey());
      if (imp != null) {
        if (req.isSynonyms()) {
          // all usages
          throwIfTooLarge(ColdpTerm.NameUsage, imp.getUsagesCount());
          throwIfTooLarge(ColdpTerm.Reference, imp.getReferenceCount());
          throwIfTooLarge(ColdpTerm.Name, imp.getNameCount());
          throwIfTooLarge(ColdpTerm.TypeMaterial, imp.getTypeMaterialCount());
          throwIfTooLarge(ColdpTerm.NameRelation, imp.getNameRelationsCount());
        } else {
          // only accepted taxa
          throwIfTooLarge(ColdpTerm.NameUsage, imp.getTaxonCount());
        }
        // these are all attached to taxa so it doesn't matter
        throwIfTooLarge(ColdpTerm.VernacularName, imp.getVernacularCount());
        throwIfTooLarge(ColdpTerm.Distribution, imp.getDistributionCount());
        throwIfTooLarge(ColdpTerm.TaxonConceptRelation, imp.getTaxonConceptRelationsCount());
        throwIfTooLarge(ColdpTerm.SpeciesInteraction, imp.getSpeciesInteractionsCount());
        throwIfTooLarge(ColdpTerm.Media, imp.getMediaCount());
      }
    }
  }

  private static void throwIfTooLarge(ColdpTerm type, Integer count){
    if (count > ExcelTermWriter.MAX_ROWS) {
      throw new IllegalArgumentException("Excel format can not be used for datasets that have more than "+ExcelTermWriter.MAX_ROWS + " " +type.simpleName() + " records");
    }
  }
}
