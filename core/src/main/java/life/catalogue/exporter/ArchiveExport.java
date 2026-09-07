package life.catalogue.exporter;

import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.common.func.ThrowingBiConsumer;
import life.catalogue.common.func.ThrowingConsumer;
import life.catalogue.common.io.TermWriter;
import life.catalogue.common.lang.InterruptedRuntimeException;
import life.catalogue.db.*;
import life.catalogue.db.mapper.*;
import life.catalogue.img.ImageService;

import org.gbif.dwc.terms.Term;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;

import jakarta.ws.rs.core.UriBuilder;

public abstract class ArchiveExport extends DatasetExportJob {
  private static final Logger LOG = LoggerFactory.getLogger(ArchiveExport.class);
  private static final String LOGO_FILENAME = "logo.png";

  protected final boolean fullDataset;
  protected final Set<String> nameIDs = new HashSet<>();
  protected final Set<String> taxonIDs = new HashSet<>();
  protected final Set<String> refIDs = new HashSet<>();
  protected final LoadingCache<String, String> refCache;
  protected final SectorInfoCache sectorInfoCache;
  private final UriBuilder logoUriBuilder;
  protected TermWriter writer;
  /** start of the pass the current writer belongs to, see newDataFile/closeWriter */
  private long passStarted;
  private final SXSSFWorkbook wb;
  protected final boolean inclTreatments;

  ArchiveExport(DataFormat requiredFormat, int userKey, ExportRequest req, SqlSessionFactory factory, ExporterConfig cfg, ImageService imageService) {
    super(req, userKey, requiredFormat, true, factory, cfg, imageService);
    logoUriBuilder = cfg.getApiUri() == null ? null : UriBuilder.fromUri(cfg.getApiUri()).path("/dataset/{key}/logo?size=ORIGINAL");
    refCache = Caffeine.newBuilder()
                       .maximumSize(10000)
                       .build(this::lookupReference);

    fullDataset = !req.hasFilter();
    inclTreatments = !req.isExcel() && req.isExtended() && requiredFormat == DataFormat.COLDP;
    if (req.isExcel()) {
      // we use SXSSF (Streaming Usermodel API) for low memory footprint
      // https://poi.apache.org/components/spreadsheet/how-to.html#sxssf
      wb = new SXSSFWorkbook(100); // keep 100 rows in memory, exceeding rows will be flushed to disk
    } else {
      wb = null;
      // only include treatments with ColDP
    }
    sectorInfoCache = new SectorInfoCache(factory, datasetKey);
  }

  private String lookupReference(String id) {
    // a short session of its own: the export used to hold one open for its entire run just for this,
    // which now that nothing queries per usage would sit idle in a transaction for the whole core pass
    // and be cut down by postgres' idle_in_transaction_session_timeout
    try (SqlSession s = factory.openSession()) {
      Reference r = s.getMapper(ReferenceMapper.class).get(DSID.of(datasetKey, id));
      return r == null ? null : r.getCitation();
    }
  }

  protected String citationByID(String refID) {
    if (!StringUtils.isBlank(refID)) {
      return refCache.get(refID);
    }
    return null;
  }

  @Override
  protected void export() throws Exception {
    try {
      init();
      exportCore();
      exportNameRels();
      exportTaxonRels();
      exportReferences();
      closeWriter();
    } catch (InterruptedRuntimeException e) {
      // per-record cancellation checks inside the Consumer lambdas below abort with the
      // unchecked variant; surface it as a checked InterruptedException so we end CANCELED
      throw e.asChecked();
    }
  }

  /**
   * Unchecked cancellation check for use inside the Consumer lambdas passed to PgUtils.consume,
   * which cannot throw the checked InterruptedException. Converted back to checked at export().
   */
  private void checkIfCancelledRuntime() {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedRuntimeException(getClass().getSimpleName() + " export of dataset " + datasetKey + " was cancelled");
    }
  }


  @Override
  protected void exportMetadata() throws IOException {
    LOG.info("Prepare export metadata");
    // add CLB logo URL if missing
    if (imageService.datasetLogoExists(dataset.getKey())) {
      if (dataset.getLogo() == null && logoUriBuilder != null) {
        dataset.setLogo(logoUriBuilder.build(dataset.getKey()));
      }
      // include logo image file
      LOG.info("Copy logo");
      imageService.copyDatasetLogo(datasetKey, new File(tmpDir, LOGO_FILENAME));
    }

    try (SqlSession session = factory.openSession(false)) {
      DatasetSourceMapper psm = session.getMapper(DatasetSourceMapper.class);

      // extract unique source datasets if sectors were given
      Set<Integer> sourceKeys = sectorInfoCache.sourceKeys();
      LOG.info("Prepare metadata for {} sources from {} sectors in export {}", sourceKeys.size(), sectorInfoCache.size(), getKey());
      // for releases and projects also include an EML for each source dataset as defined by all sectors
      for (Integer sk : sourceKeys) {
        Dataset src = null;
        if (DatasetOrigin.PROJECT == dataset.getOrigin()) {
          src = psm.getProjectSource(sk, datasetKey);
        } else if (dataset.getOrigin().isRelease()) {
          src = psm.getReleaseSource(sk, datasetKey);
        }
        if (src == null) {
          LOG.warn("Skip missing source dataset {} for archive metadata", sk);
        } else {
          // create source entry in dataset
          dataset.addSource(src.toCitation());
          LOG.info("Write source metadata for {}: {}", src.getKey(), src.getTitle());
          writeSourceMetadata(src);
        }
      }
    }

    // main dataset metadata
    LOG.info("Write metadata for export {}", getKey());
    writeMetadata(dataset);
  }

  abstract void writeMetadata(Dataset dataset) throws IOException;

  abstract void writeSourceMetadata(Dataset source) throws IOException;

  @Override
  protected void bundle() throws IOException, InterruptedException {
    checkIfCancelled();
    // write workbook to single file and cleanup temp POI files
    if (wb != null) {
      LOG.info("Writing final Excel file");
      FileOutputStream out = new FileOutputStream(new File(tmpDir, "data.xlsx"));
      wb.write(out);
      out.close();
      // dispose of temporary files backing this workbook on disk
      LOG.info("Dispose temporary Excel files");
      wb.dispose();
    }
    super.bundle();
  }

  protected void init() throws Exception {
    // nothing by default - subclasses hook in here
  }

  /**
   * How many ids a filtered export asks for at once.
   *
   * It used to fetch them one at a time - a round trip per taxon or name, per entity type - and then, for a
   * while, to stream the whole entity and filter here, which read far more than it kept: a subtree export of
   * the COL XRelease discarded 98% of the distribution rows it scanned, and the cost did not shrink with the
   * size of the download. Batches do neither.
   *
   * Kept in the low thousands on purpose. Handed a very large array postgres stops doing an index nested
   * loop and hashes the whole partition instead, which is the scan we are trying to get away from.
   */
  @VisibleForTesting
  static int ID_BATCH_SIZE = 5_000;

  /**
   * The collected ids of a filtered export, in batches. A full dataset export never asks: it streams.
   */
  private Iterable<List<String>> batches(Set<String> ids) {
    return Iterables.partition(ids, ID_BATCH_SIZE);
  }

  private void exportCore() throws IOException, InterruptedException {
    if (!newDataFile(define(EntityType.NAME_USAGE))) {
      throw new IllegalStateException("Core name usage data must be exported");
    }
    checkIfCancelled();
    try (SqlSession session = factory.openSession()) {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      final Cursor<NameUsageBase> cursor;
      if (fullDataset) {
        cursor = num.processDatasetWithClassification(datasetKey, null, null, inclCitations());
      } else {
        var ttp = TreeTraversalParameter.dataset(datasetKey, req.getTaxonID(), null, req.getMinRank(), req.isSynonyms());
        cursor = num.processTree(ttp, false, false, true, inclCitations());
      }
      checkIfCancelled();
      // iterate manually (not PgUtils.consume) so the per-record consumeUsage
      // can propagate its checked InterruptedException for responsive cancellation
      try (cursor) {
        for (NameUsageBase u : cursor) {
          consumeUsage(u);
        }
      }

      // add bare names?
      checkIfCancelled();
      if (req.isBareNames()) {
        try (var bareNames = num.processDatasetBareNames(datasetKey, null, null, true, inclCitations())) {
          for (BareName u : bareNames) {
            consumeUsage(u);
          }
        }
      }

    } catch (RuntimeException e) {
      catchTruncation(e);
    } finally {
      taxonIDs.remove(null); // can happen
      nameIDs.remove(null); // can happen
    }
  }

  private void catchTruncation(RuntimeException e){
    if (e.getCause() instanceof ExcelTermWriter.MaxRowsException) {
      // we truncate the output and keep a warning, but allow to proceed
      LOG.warn(e.getCause().getMessage());
      getExport().addTruncated(writer.getRowType());
    } else {
      // anything else is unexpected
      throw e;
    }
  }

  private void consumeUsage(NameUsageBase u) throws InterruptedException {
    checkIfCancelled();
    if (!fullDataset) {
      if (req.getExtinct() != null) {
        // filter out usages as the tree traversal cannot do that
        if (u.isTaxon() && !Objects.equals(req.getExtinct(), ObjectUtils.coalesce(u.asTaxon().isExtinct(), false))) {
          return;
        }
        // also remove synonyms of filtered accepted taxa
        if (u.isSynonym() && !taxonIDs.contains(u.getParentId())) {
          return;
        }
      }
      refIDs.add(u.getName().getPublishedInId());
      refIDs.addAll(u.getReferenceIds());
      refIDs.add(u.getAccordingToId());
      nameIDs.add(u.getName().getId());
      if (u.isTaxon()) {
        taxonIDs.add(u.getId());
      }
    }
    // metrics
    counter.inc(u);

    try {
      u.setSectorMode(sectorInfoCache.sector2mode(u.getSectorKey()));
      write(u);
      writer.next();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void consumeUsage(BareName u) throws InterruptedException {
    checkIfCancelled();
    if (!fullDataset) {
      nameIDs.add(u.getName().getId());
      refIDs.add(u.getName().getPublishedInId());
    }
    // metrics
    counter.inc(u);

    try {
      u.setSectorMode(sectorInfoCache.sector2mode(u.getSectorKey()));
      write(u);
      writer.next();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void exportNameRels() throws IOException, InterruptedException {
    exportNameRelation(EntityType.NAME_RELATION, NameRelationMapper.class, this::write);
    exportNameRelation(EntityType.TYPE_MATERIAL, TypeMaterialMapper.class, this::write);
  }

  private void exportTaxonRels() throws IOException, InterruptedException {
    exportTaxonExtension(EntityType.VERNACULAR, VernacularNameMapper.class, this::write);
    exportTaxonExtension(EntityType.DISTRIBUTION, DistributionMapper.class, this::write);
    exportTaxonExtension(EntityType.MEDIA, MediaMapper.class, this::write);
    exportTaxonExtension(EntityType.TAXON_PROPERTY, TaxonPropertyMapper.class, this::write);
    exportTreatments();
    exportEstimates();
    exportTaxonRelation(EntityType.SPECIES_INTERACTION, SpeciesInteractionMapper.class, this::write);
    exportTaxonRelation(EntityType.TAXON_CONCEPT_RELATION, TaxonConceptRelationMapper.class, this::write);
  }

  protected void exportReferences() throws IOException, InterruptedException {
    checkIfCancelled();
    if (newDataFile(define(EntityType.REFERENCE))) {
      try (SqlSession session = factory.openSession()) {
        ReferenceMapper rm = session.getMapper(ReferenceMapper.class);
        if (fullDataset) {
          PgUtils.consume(()->rm.processDataset(datasetKey), r -> {
            checkIfCancelledRuntime();
            try {
              r.setSectorMode(sectorInfoCache.sector2mode(r.getSectorKey()));
              write(r);
              writer.next();
            } catch (final IOException e) {
              throw new RuntimeException(e);
            }
          });
        } else {
          // references are exported last, so refIDs is complete by now
          refIDs.remove(null); // can happen
          int found = 0;
          for (List<String> batch : batches(refIDs)) {
            checkIfCancelledRuntime();
            for (Reference ref : rm.listByIds(datasetKey, Set.copyOf(batch))) {
              ref.setSectorMode(sectorInfoCache.sector2mode(ref.getSectorKey()));
              write(ref);
              writer.next();
              found++;
            }
          }
          // the per id path named each dangling reference; batches can only count them
          if (found < refIDs.size()) {
            LOG.warn("{} of {} referenced reference IDs do not exist in dataset {}", refIDs.size()-found, refIDs.size(), datasetKey);
          }
        }
      }
    }
  }

  private <T extends ExtensionEntity> void exportTaxonExtension(EntityType entity, Class < ? extends TaxonExtensionMapper<T>> mapperClass, ThrowingBiConsumer < String, T, IOException > consumer) throws IOException, InterruptedException {
    checkIfCancelled();
    if (newDataFile(define(entity))) {
      try (SqlSession session = factory.openSession()) {
        TaxonExtensionMapper<T> exm = session.getMapper(mapperClass);
        if (fullDataset) {
          PgUtils.consume(()->exm.processDataset(datasetKey), x -> {
            checkIfCancelledRuntime();
            try {
              writeExtension(x, consumer);
            } catch (final IOException e) {
              throw new RuntimeException(e);
            }
          });

        } else {
          for (List<String> batch : batches(taxonIDs)) {
            checkIfCancelledRuntime();
            for (TaxonExtension<T> x : exm.listByTaxa(datasetKey, batch)) {
              writeExtension(x, consumer);
            }
          }
        }
      } catch (RuntimeException e) {
        catchTruncation(e);
      }
    }
  }

  private <T extends SectorScopedEntity<?> & Referenced> void writeRelation(T x, ThrowingConsumer<T, IOException> consumer) throws IOException {
    trackRefId(x);
    x.setSectorMode(sectorInfoCache.sector2mode(x.getSectorKey()));
    consumer.accept(x);
    writer.next();
  }

  private <T extends ExtensionEntity> void writeExtension(TaxonExtension<T> x, ThrowingBiConsumer<String, T, IOException> consumer) throws IOException {
    trackRefId(x.getObj());
    x.getObj().setSectorMode(sectorInfoCache.sector2mode(x.getObj().getSectorKey()));
    consumer.accept(x.getTaxonID(), x.getObj());
    this.writer.next();
  }

  private <T extends SectorScopedEntity<?> & Referenced, M extends NameProcessable<T> & DatasetProcessable<T> & NameBatchable<T>> void exportNameRelation(EntityType type, Class<M> mapperClass, ThrowingConsumer<T, IOException> consumer) throws IOException, InterruptedException {
    checkIfCancelled();
    new NameRelExporter<T, M>().export(type, mapperClass, consumer);
  }

  private class NameRelExporter<T extends SectorScopedEntity<?> & Referenced, M extends NameProcessable<T> & DatasetProcessable<T> & NameBatchable<T>> {
    void export(EntityType entity, Class<M> mapperClass, ThrowingConsumer<T, IOException> consumer) throws IOException, InterruptedException {
      if (newDataFile(define(entity))) {
        try (SqlSession session = factory.openSession()) {
          M mapper = session.getMapper(mapperClass);
          if (fullDataset) {
            PgUtils.consume(()->mapper.processDataset(datasetKey), x -> {
              checkIfCancelledRuntime();
              try {
                writeRelation(x, consumer);
              } catch (final IOException e) {
                throw new RuntimeException(e);
              }
            });
          } else {
            for (List<String> batch : batches(nameIDs)) {
              checkIfCancelledRuntime();
              for (T x : mapper.listByNames(datasetKey, batch)) {
                writeRelation(x, consumer);
              }
            }
          }
        } catch (RuntimeException e) {
          catchTruncation(e);
        }
      }
    }
  }

  private <T extends SectorScopedEntity<Integer> & Referenced, M extends TaxonProcessable<T> & DatasetProcessable<T> & TaxonBatchable<T>> void exportTaxonRelation(EntityType type, Class<M> mapperClass, ThrowingConsumer<T, IOException> consumer) throws IOException, InterruptedException {
    checkIfCancelled();
    new TaxonRelExporter<T, M>().export(type, mapperClass, consumer);
  }

  private class TaxonRelExporter<T extends SectorScopedEntity<Integer> & Referenced, M extends TaxonProcessable<T> & DatasetProcessable<T> & TaxonBatchable<T>> {
    void export(EntityType entity, Class<M> mapperClass, ThrowingConsumer<T, IOException> consumer) throws IOException {
      if (newDataFile(define(entity))) {
        try (SqlSession session = factory.openSession()) {
          M mapper = session.getMapper(mapperClass);
          if (fullDataset) {
            PgUtils.consume(()->mapper.processDataset(datasetKey), x -> {
              checkIfCancelledRuntime();
              try {
                writeRelation(x, consumer);
              } catch (final IOException e) {
                throw new RuntimeException(e);
              }
            });
          } else {
            for (List<String> batch : batches(taxonIDs)) {
              checkIfCancelledRuntime();
              for (T x : mapper.listByTaxa(datasetKey, batch)) {
                writeRelation(x, consumer);
              }
            }
          }
        } catch (RuntimeException e) {
          catchTruncation(e);
        }
      }
    }
  }

  void writeTreatment(Treatment t) throws IOException {
    // nothing by default - only ColDP supports this
  }

  private void exportTreatments() throws IOException, InterruptedException {
    checkIfCancelled();
    if (inclTreatments) {
      final long started = System.currentTimeMillis();
      final AtomicInteger treatments = new AtomicInteger();
      try (SqlSession session = factory.openSession()) {
        var mapper = session.getMapper(TreatmentMapper.class);
        if (fullDataset) {
          PgUtils.consume(()->mapper.processDataset(datasetKey), x -> {
            checkIfCancelledRuntime();
            try {
              writeTreatment(x);
              treatments.incrementAndGet();
            } catch (final IOException e) {
              throw new RuntimeException(e);
            }
          });
        } else {
          for (List<String> batch : batches(taxonIDs)) {
            checkIfCancelledRuntime();
            for (var x : mapper.listByTaxa(datasetKey, batch)) {
              writeTreatment(x);
              treatments.incrementAndGet();
            }
          }
        }
      }
      logPass("Treatment", treatments.get(), started);
    }
  }

  private void exportEstimates() throws IOException, InterruptedException {
    checkIfCancelled();
    if (newDataFile(define(EntityType.ESTIMATE))) {
      try (SqlSession session = factory.openSession()) {
        EstimateMapper mapper = session.getMapper(EstimateMapper.class);
        if (fullDataset) {
          PgUtils.consume(()->mapper.processDataset(datasetKey), x -> {
            checkIfCancelledRuntime();
            try {
              trackRefId(x);
              write(x);
              writer.next();
            } catch (final IOException e) {
              throw new RuntimeException(e);
            }
          });
        } else {
          for (List<String> batch : batches(taxonIDs)) {
            checkIfCancelledRuntime();
            for (SpeciesEstimate x : mapper.listByTaxa(datasetKey, batch)) {
              trackRefId(x);
              write(x);
              writer.next();
            }
          }
        }
      } catch (RuntimeException e) {
        catchTruncation(e);
      }
    }
  }

  private void closeWriter() throws IOException {
    if (writer != null) {
      closeAdditionalWriters(writer.getRowType());
      writer.close();
      logPass(writer.getRowType().simpleName(), writer.getCounter(), passStarted);
      writer = null;
    }
  }

  /**
   * Logs how long one entity pass took and how fast it went, so a slow export can be attributed
   * to a single entity from the job log alone.
   */
  private void logPass(String rowType, int records, long startedInMillis) {
    long ms = System.currentTimeMillis() - startedInMillis;
    LOG.info("Exported {} {} records from dataset {} in {} ({} records/s)", records, rowType, datasetKey,
      DurationFormatUtils.formatDurationHMS(ms), ms > 0 ? 1000L * records / ms : records);
  }

  private boolean newDataFile(Term[] terms) throws IOException {
    closeWriter();
    if (terms != null && terms.length>2) {
      Term rowType = terms[0];
      var cols = List.of(Arrays.copyOfRange(terms, 1, terms.length));
      LOG.info("Export {} from dataset {}", rowType.simpleName(), datasetKey);
      if (req.isExcel()) {
        writer = new ExcelTermWriter(wb, rowType, cols);
      } else {
        writer = new TermWriter.TSV(tmpDir, rowType, cols);
      }
      passStarted = System.currentTimeMillis();
      openAdditionalWriters(rowType);
      return true;
    }
    return false;
  }

  protected void openAdditionalWriters(Term rowType) throws IOException {
    // nothing to be done by default, to be subclassed if needed, e.g. with references
  }

  protected void closeAdditionalWriters(Term rowType) throws IOException {
    // nothing to be done by default, to be subclassed if needed, e.g. with references
  }


    /**
     * Defines the terms to be used for a data file of a given entity.
     * If NULL is returned the entity is to be ignored in the archive.
     * The first term MUST be the row type class term.
     * The second term MUST be the ID term if there is one
     * The following terms are other terms to be included in the given order.
     */
  abstract Term[] define(EntityType entity);

  /**
   * Whether the core query should join in the publishedIn and accordingTo reference citations.
   * Only formats that write citations inline instead of reference ids want them - they are wide columns.
   */
  boolean inclCitations() {
    return false;
  }

  void write(NameUsageBase u){
  }

  void write(BareName n){
  }

  void write(Reference r) throws IOException {
  }

  void write(NameRelation rel) {
  }

  void write(TypeMaterial tm) {
  }

  void write(TaxonConceptRelation rel) {
  }

  void write(SpeciesInteraction rel) {
  }

  void write(String taxonID, VernacularName vn) {
  }

  void write(String taxonID, Distribution d) {
  }

  void write(String taxonID, Media m) {
  }

  void write(String taxonID, TaxonProperty tp) {
  }

  void write(SpeciesEstimate e) {
  }

  private void trackRefId(Referenced referenced) {
    if (!fullDataset) {
      refIDs.add(referenced.getReferenceId());
    }
  }
}
