package life.catalogue.exporter;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import life.catalogue.api.model.*;
import life.catalogue.api.search.EstimateSearchRequest;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.common.func.ThrowingBiConsumer;
import life.catalogue.common.func.ThrowingConsumer;
import life.catalogue.common.io.TermWriter;
import life.catalogue.db.DatasetProcessable;
import life.catalogue.db.NameProcessable;
import life.catalogue.db.TaxonProcessable;
import life.catalogue.db.mapper.*;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.dwc.terms.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

abstract class ArchiveExporter extends DatasetExporter {
  private static final Logger LOG = LoggerFactory.getLogger(ArchiveExporter.class);

  protected boolean fullDataset;
  protected final Set<String> nameIDs = new HashSet<>();
  protected final Set<String> taxonIDs = new HashSet<>();
  protected final Set<String> refIDs = new HashSet<>();
  protected final LoadingCache<String, String> refCache;
  protected SqlSession session;
  protected TermWriter writer;
  protected final DSID<String> key = DSID.of(datasetKey, "");

  ArchiveExporter(ExportRequest req, SqlSessionFactory factory, File exportDir) {
    super(req, factory, exportDir);
    this.archive = archive(exportDir, getKey());
    this.tmpDir = new File(exportDir, getKey().toString());
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
    LOG.info("Created exporter job {} for dataset {} to {}", getKey(), datasetKey, archive);
  }

  @Override
  public void export() throws Exception {
    // do we have a full dataset export request?
    fullDataset = req.isSynonyms() && (req.getExclusions() == null || req.getExclusions().isEmpty()) && req.getTaxonID()==null && req.getMinRank()==null;
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

  protected void init(SqlSession session) {

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
        cursor = num.processTree(datasetKey, null, req.getTaxonID(), req.getExclusions(), req.getMinRank(), req.isSynonyms(), true);
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
    new NameRelExporter<NameRelation, NameRelationMapper>()
      .export(EntityType.NAME_RELATION, NameRelationMapper.class, this::write);
    new NameRelExporter<TypeMaterial, TypeMaterialMapper>()
      .export(EntityType.TYPE_MATERIAL, TypeMaterialMapper.class, this::write);

  }

  private void exportTaxonRels() throws IOException {
    exportTaxonExtension(EntityType.VERNACULAR, VernacularNameMapper.class, this::write);
    exportTaxonExtension(EntityType.DISTRIBUTION, DistributionMapper.class, this::write);
    exportTaxonExtension(EntityType.MEDIA, MediaMapper.class, this::write);
    exportEstimates();
    new TaxonRelExporter<SpeciesInteraction, SpeciesInteractionMapper>()
      .export(EntityType.SPECIES_INTERACTION, SpeciesInteractionMapper.class, this::write);
    new TaxonRelExporter<TaxonConceptRelation, TaxonConceptRelationMapper>()
      .export(EntityType.TAXON_CONCEPT_RELATION, TaxonConceptRelationMapper.class, this::write);

  }

  private void exportReferences() throws IOException {
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

  abstract void exportMetadata(Dataset d);

  private void closeWriter() throws IOException {
    if (writer != null) {
      writer.close();
      writer = null;
    }
  }

  private boolean newDataFile(Term[] terms) throws IOException {
    closeWriter();
    if (terms != null) {
      LOG.info("Export {} from dataset {}", terms[0].simpleName(), datasetKey);
      writer = new TermWriter(tmpDir, terms[0], terms[1], List.of(Arrays.copyOfRange(terms, 2, terms.length)));
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

  void write(Reference r){

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
