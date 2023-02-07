package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.*;
import life.catalogue.api.search.EstimateSearchRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.common.func.ThrowingBiConsumer;
import life.catalogue.common.func.ThrowingConsumer;
import life.catalogue.common.io.TermWriter;
import life.catalogue.db.DatasetProcessable;
import life.catalogue.db.NameProcessable;
import life.catalogue.db.PgUtils;
import life.catalogue.db.TaxonProcessable;
import life.catalogue.db.mapper.*;
import life.catalogue.img.ImageService;

import org.apache.commons.lang3.StringUtils;

import org.apache.poi.ss.formula.functions.T;

import org.gbif.dwc.terms.Term;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.UriBuilder;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Timer;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

public abstract class ArchiveExport extends DatasetExportJob {
  private static final Logger LOG = LoggerFactory.getLogger(ArchiveExport.class);
  private static final String LOGO_FILENAME = "logo.png";

  protected boolean fullDataset;
  protected final Set<String> nameIDs = new HashSet<>();
  protected final Set<String> taxonIDs = new HashSet<>();
  protected final Set<String> refIDs = new HashSet<>();
  protected final LoadingCache<String, String> refCache;
  protected final Int2IntMap sector2datasetKeys = new Int2IntOpenHashMap();
  private final UriBuilder logoUriBuilder;
  protected SectorMapper sectorMapper;
  protected NameRelationMapper nameRelMapper;
  protected SqlSession session;
  protected TermWriter writer;
  protected final DSID<String> entityKey = DSID.of(datasetKey, "");
  private final SXSSFWorkbook wb;

  ArchiveExport(DataFormat requiredFormat, int userKey, ExportRequest req, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService, Timer timer) {
    super(req, userKey, requiredFormat, true, factory, cfg, imageService, timer);
    logoUriBuilder = cfg.apiURI == null ? null : UriBuilder.fromUri(cfg.apiURI).path("/dataset/{key}/logo?size=ORIGINAL");
    refCache = Caffeine.newBuilder()
                       .maximumSize(10000)
                       .build(this::lookupReference);

    if (req.isExcel()) {
      // we use SXSSF (Streaming Usermodel API) for low memory footprint
      // https://poi.apache.org/components/spreadsheet/how-to.html#sxssf
      wb = new SXSSFWorkbook(100); // keep 100 rows in memory, exceeding rows will be flushed to disk
    } else {
      wb = null;
    }
  }

  private String lookupReference(String id) {
    Reference r = session.getMapper(ReferenceMapper.class).get(DSID.of(datasetKey, id));
    return r == null ? null : r.getCitation();
  }

  protected String citationByID(String refID) {
    if (!StringUtils.isBlank(refID)) {
      return refCache.get(refID);
    }
    return null;
  }

  protected Integer sector2datasetKey(Integer sectorKey){
    if (sectorKey != null) {
      int sk = sectorKey;
      if (!sector2datasetKeys.containsKey(sk)) {
        Sector s = sectorMapper.get(DSID.of(datasetKey, sectorKey));
        // we apparently have references that still link to removed sectors - don't fail
        sector2datasetKeys.put(sk, s==null ? -1 : s.getSubjectDatasetKey());
      }
      int dkey = sector2datasetKeys.get(sk);
      return dkey<0 ? null : dkey;
    }
    return null;
  }

  @Override
  public void export() throws Exception {
    // do we have a full dataset export request?
    fullDataset = !req.hasFilter();
    try (SqlSession session = factory.openSession(false)) {
      this.session = session;
      init(session);
      exportCore();
      exportNameRels();
      exportTaxonRels();
      exportReferences();
      closeWriter();
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
      Set<Integer> sourceKeys = new HashSet<>(sector2datasetKeys.values());
      LOG.info("Prepare metadata for {} sources from {} sectors in export {}", sourceKeys.size(), sector2datasetKeys.size(), getKey());
      // for releases and projects also include an EML for each source dataset as defined by all sectors
      for (Integer sk : sourceKeys) {
        Dataset src = null;
        if (DatasetOrigin.PROJECT == dataset.getOrigin()) {
          src = psm.getProjectSource(sk, datasetKey);
        } else if (DatasetOrigin.RELEASE == dataset.getOrigin()) {
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
  protected void bundle() throws IOException {
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

  protected void init(SqlSession session) throws Exception {
    sectorMapper = session.getMapper(SectorMapper.class);
    nameRelMapper = session.getMapper(NameRelationMapper.class);
  }

  private void exportCore() throws IOException {
    if (!newDataFile(define(EntityType.NAME_USAGE))) {
      throw new IllegalStateException("Core name usage data must be exported");
    }
    try (SqlSession session = factory.openSession()) {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      final Cursor<NameUsageBase> cursor;
      if (fullDataset) {
        cursor = num.processDataset(datasetKey, null, null);
      } else {
        cursor = num.processTree(datasetKey, null, req.getTaxonID(), null, req.getMinRank(), req.isSynonyms(), true);
      }
      PgUtils.consume(() -> cursor, this::consumeUsage);

      // add bare names?
      if (req.isBareNames()) {
        num.processDatasetBareNames(datasetKey, null, null).forEach(this::consumeUsage);
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

  private void consumeUsage(NameUsageBase u){
    if (!fullDataset && u.isTaxon()) {
      taxonIDs.add(u.getId());
      nameIDs.add(u.getName().getId());
      refIDs.add(u.getName().getPublishedInId());
      refIDs.add(u.getAccordingToId());
      refIDs.addAll(u.getReferenceIds());
    }
    // metrics
    counter.inc(u);

    try {
      write(u);
      writer.next();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void consumeUsage(BareName u){
    if (!fullDataset) {
      nameIDs.add(u.getName().getId());
      refIDs.add(u.getName().getPublishedInId());
    }
    // metrics
    counter.inc(u);

    try {
      write(u);
      writer.next();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void exportNameRels() throws IOException {
    exportNameRelation(EntityType.NAME_RELATION, NameRelationMapper.class, this::write);
    exportNameRelation(EntityType.TYPE_MATERIAL, TypeMaterialMapper.class, this::write);
  }

  private void exportTaxonRels() throws IOException {
    exportTaxonExtension(EntityType.VERNACULAR, VernacularNameMapper.class, this::write);
    exportTaxonExtension(EntityType.DISTRIBUTION, DistributionMapper.class, this::write);
    exportTaxonExtension(EntityType.MEDIA, MediaMapper.class, this::write);
    exportEstimates();
    exportTaxonRelation(EntityType.SPECIES_INTERACTION, SpeciesInteractionMapper.class, this::write);
    exportTaxonRelation(EntityType.TAXON_CONCEPT_RELATION, TaxonConceptRelationMapper.class, this::write);
  }

  protected void exportReferences() throws IOException {
    if (newDataFile(define(EntityType.REFERENCE))) {
      try (SqlSession session = factory.openSession()) {
        ReferenceMapper rm = session.getMapper(ReferenceMapper.class);
        if (fullDataset) {
          PgUtils.consume(()->rm.processDataset(datasetKey), r -> {
            try {
              write(r);
              writer.next();
            } catch (final IOException e) {
              throw new RuntimeException(e);
            }
          });
        } else {
          refIDs.remove(null); // can happen
          for (String id : refIDs) {
            var ref = rm.get(entityKey.id(id));
            if (ref != null) {
              write(ref);
              writer.next();
            } else {
              LOG.warn("Reference ID {} used but does not exist in dataset {}", id, datasetKey);
            }
          }
        }
      }
    }
  }

  private <T extends SectorScopedEntity<Integer> &Referenced > void exportTaxonExtension(EntityType entity, Class < ? extends TaxonExtensionMapper<T>> mapperClass, ThrowingBiConsumer < String, T, IOException > consumer) throws IOException {
    if (newDataFile(define(entity))) {
      try (SqlSession session = factory.openSession()) {
        TaxonExtensionMapper<T> exm = session.getMapper(mapperClass);
        if (fullDataset) {
          PgUtils.consume(()->exm.processDataset(datasetKey), x -> {
            try {
              trackRefId(x.getObj());
              consumer.accept(x.getTaxonID(), x.getObj());
              this.writer.next();
            } catch (final IOException e) {
              throw new RuntimeException(e);
            }
          });

        } else {
          for (String id : taxonIDs) {
            for (T x : exm.listByTaxon(entityKey.id(id))) {
              trackRefId(x);
              consumer.accept(id, x);
              this.writer.next();
            }
          }
        }
      } catch (RuntimeException e) {
        catchTruncation(e);
      }
    }
  }

  private <T extends DatasetScopedEntity & Referenced, M extends NameProcessable<T> & DatasetProcessable<T>> void exportNameRelation(EntityType type, Class<M> mapperClass, ThrowingConsumer<T, IOException> consumer) throws IOException {
    new NameRelExporter<T, M>().export(type, mapperClass, consumer);
  }

  private class NameRelExporter<T extends DatasetScopedEntity & Referenced, M extends NameProcessable<T> & DatasetProcessable<T>> {
    void export(EntityType entity, Class<M> mapperClass, ThrowingConsumer<T, IOException> consumer) throws IOException {
      if (newDataFile(define(entity))) {
        try (SqlSession session = factory.openSession()) {
          M mapper = session.getMapper(mapperClass);
          if (fullDataset) {
            PgUtils.consume(()->mapper.processDataset(datasetKey), x -> {
              try {
                trackRefId(x);
                consumer.accept(x);
                writer.next();
              } catch (final IOException e) {
                throw new RuntimeException(e);
              }
            });
          } else {
            for (String id : nameIDs) {
              for (T x : mapper.listByName(entityKey.id(id))) {
                trackRefId(x);
                consumer.accept(x);
                writer.next();
              }
            }
          }
        } catch (RuntimeException e) {
          catchTruncation(e);
        }
      }
    }
  }

  private <T extends DatasetScopedEntity<Integer> & Referenced, M extends TaxonProcessable<T> & DatasetProcessable<T>> void exportTaxonRelation(EntityType type, Class<M> mapperClass, ThrowingConsumer<T, IOException> consumer) throws IOException {
    new TaxonRelExporter<T, M>().export(type, mapperClass, consumer);
  }

  private class TaxonRelExporter<T extends DatasetScopedEntity<Integer> & Referenced, M extends TaxonProcessable<T> & DatasetProcessable<T>> {
    void export(EntityType entity, Class<M> mapperClass, ThrowingConsumer<T, IOException> consumer) throws IOException {
      if (newDataFile(define(entity))) {
        try (SqlSession session = factory.openSession()) {
          M mapper = session.getMapper(mapperClass);
          if (fullDataset) {
            PgUtils.consume(()->mapper.processDataset(datasetKey), x -> {
              try {
                trackRefId(x);
                consumer.accept(x);
                writer.next();
              } catch (final IOException e) {
                throw new RuntimeException(e);
              }
            });
          } else {
            for (String id : taxonIDs) {
              for (T x : mapper.listByTaxon(entityKey.id(id))) {
                trackRefId(x);
                consumer.accept(x);
                writer.next();
              }
            }
          }
        } catch (RuntimeException e) {
          catchTruncation(e);
        }
      }
    }
  }

  private void exportEstimates() throws IOException {
    if (newDataFile(define(EntityType.ESTIMATE))) {
      try (SqlSession session = factory.openSession()) {
        EstimateMapper mapper = session.getMapper(EstimateMapper.class);
        if (fullDataset) {
          PgUtils.consume(()->mapper.processDataset(datasetKey), x -> {
            try {
              trackRefId(x);
              write(x);
              writer.next();
            } catch (final IOException e) {
              throw new RuntimeException(e);
            }
          });
        } else {
          Page page = new Page(0,100);
          EstimateSearchRequest req = new EstimateSearchRequest();
          req.setDatasetKey(datasetKey);
          for (String id : taxonIDs) {
            req.setId(id);
            for (SpeciesEstimate x : mapper.search(req, page)) {
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
      writer.close();
      writer = null;
    }
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
      return true;
    }
    return false;
  }

  /**
   * Defines the terms to be used for a data file of a given entity.
   * If NULL is returned the entity is to be ignored in the archive.
   * The first term MUST be the row type class term.
   * The second term MUST be the ID term if there is one
   * The following terms are other terms to be included in the given order.
   */
  abstract Term[] define(EntityType entity);

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

  void write(SpeciesEstimate e) {
  }

  private void trackRefId(Referenced referenced) {
    if (!fullDataset) {
      refIDs.add(referenced.getReferenceId());
    }
  }
}
