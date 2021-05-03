package life.catalogue.exporter;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.*;
import life.catalogue.api.search.EstimateSearchRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.common.func.ThrowingBiConsumer;
import life.catalogue.common.func.ThrowingConsumer;
import life.catalogue.common.io.TermWriter;
import life.catalogue.db.DatasetProcessable;
import life.catalogue.db.NameProcessable;
import life.catalogue.db.TaxonProcessable;
import life.catalogue.db.mapper.*;
import life.catalogue.img.ImageService;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.gbif.dwc.terms.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class ArchiveExporter extends DatasetExporter {
  private static final Logger LOG = LoggerFactory.getLogger(ArchiveExporter.class);

  protected boolean fullDataset;
  protected final Set<String> nameIDs = new HashSet<>();
  protected final Set<String> taxonIDs = new HashSet<>();
  protected final Set<String> refIDs = new HashSet<>();
  protected final LoadingCache<String, String> refCache;
  protected final Int2IntMap sector2datasetKeys = new Int2IntOpenHashMap();
  protected SectorMapper sectorMapper;
  protected ProjectSourceMapper projectSourceMapper;
  protected NameRelationMapper nameRelMapper;
  protected SqlSession session;
  protected TermWriter writer;
  protected final DSID<String> key = DSID.of(datasetKey, "");
  private final SXSSFWorkbook wb;

  ArchiveExporter(DataFormat requiredFormat, int userKey, ExportRequest req, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService) {
    super(req, userKey, requiredFormat, factory, cfg, imageService);
    final DSID<String> rKey = DSID.of(datasetKey, null);
    this.refCache = CacheBuilder.newBuilder()
      .maximumSize(1000)
      .build(new CacheLoader<>() {
        @Override
        public String load(String key) throws Exception {
          Reference r = session.getMapper(ReferenceMapper.class).get(rKey.id(key));
          return r == null ? null : r.getCitation();
        }
      });
    if (req.isExcel()) {
      // we use SXSSF (Streaming Usermodel API) for low memory footprint
      // https://poi.apache.org/components/spreadsheet/how-to.html#sxssf
      wb = new SXSSFWorkbook(100); // keep 100 rows in memory, exceeding rows will be flushed to disk
    } else {
      wb = null;
    }
  }

  protected Integer sector2datasetKey(Integer sectorKey){
    if (sectorKey != null) {
      int sk = sectorKey;
      if (!sector2datasetKeys.containsKey(sk)) {
        Sector s = sectorMapper.get(DSID.of(datasetKey, sectorKey));
        sector2datasetKeys.put(sk, (int) s.getSubjectDatasetKey());
      }
      sector2datasetKeys.get(sk);
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
      exportMetadata(dataset);
    }
  }

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
    projectSourceMapper = session.getMapper(ProjectSourceMapper.class);
    nameRelMapper = session.getMapper(NameRelationMapper.class);
  }

  private void exportCore() throws IOException {
    if (!newDataFile(define(EntityType.NAME_USAGE))) {
      throw new IllegalStateException("Core name usage data must be exported");
    }
    try (SqlSession session = factory.openSession()) {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      Cursor<NameUsageBase> cursor;
      if (fullDataset) {
        cursor = num.processDataset(datasetKey, null, null);
      } else {
        cursor = num.processTree(datasetKey, null, req.getTaxonID(), null, req.getMinRank(), req.isSynonyms(), true);
      }
      cursor.forEach(this::consumeUsage);
      taxonIDs.remove(null); // can happen
      nameIDs.remove(null); // can happen
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
          rm.processDataset(datasetKey).forEach(r -> {
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
            write(rm.get(key.id(id)));
            writer.next();
          }
        }
      }
    }
  }

  private <T extends SectorScopedEntity<Integer> & Referenced> void exportTaxonExtension(EntityType entity, Class<? extends TaxonExtensionMapper<T>> mapperClass, ThrowingBiConsumer<String, T, IOException> consumer) throws IOException {
    if (newDataFile(define(entity))) {
      try (SqlSession session = factory.openSession()) {
        TaxonExtensionMapper<T> exm = session.getMapper(mapperClass);
        if (fullDataset) {
          exm.processDataset(datasetKey).forEach(x -> {
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
            for (T x : exm.listByTaxon(key.id(id))) {
              trackRefId(x);
              consumer.accept(id, x);
              this.writer.next();
            }
          }
        }
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
            mapper.processDataset(datasetKey).forEach(x -> {
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
              for (T x : mapper.listByName(key.id(id))) {
                trackRefId(x);
                consumer.accept(x);
                writer.next();
              }
            }
          }
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
            mapper.processDataset(datasetKey).forEach(x -> {
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
              for (T x : mapper.listByTaxon(key.id(id))) {
                trackRefId(x);
                consumer.accept(x);
                writer.next();
              }
            }
          }
        }
      }
    }
  }

  private void exportEstimates() throws IOException {
    if (newDataFile(define(EntityType.ESTIMATE))) {
      try (SqlSession session = factory.openSession()) {
        EstimateMapper mapper = session.getMapper(EstimateMapper.class);
        if (fullDataset) {
          mapper.processDataset(datasetKey).forEach(x -> {
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
      }
    }
  }

  abstract void exportMetadata(Dataset d) throws IOException;

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
      Term idTerm = terms[1];
      var cols = List.of(Arrays.copyOfRange(terms, 2, terms.length));
      LOG.info("Export {} from dataset {}", rowType.simpleName(), datasetKey);
      if (req.isExcel()) {
        writer = new ExcelTermWriter(wb, rowType, idTerm, cols);
      } else {
        writer = new TermWriter.CSV(tmpDir, rowType, idTerm, cols);
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
