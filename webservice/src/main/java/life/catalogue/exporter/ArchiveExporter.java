package life.catalogue.exporter;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.common.func.ThrowingBiConsumer;
import life.catalogue.common.io.TermWriter;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.ReferenceMapper;
import life.catalogue.db.mapper.TaxonExtensionMapper;
import life.catalogue.db.mapper.VernacularNameMapper;
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

  private final int maxUsageIDs = 10000;

  protected final Set<String> taxonIDs = new HashSet<>();
  protected final LoadingCache<String, String> refCache;
  protected SqlSession session;
  protected TermWriter writer;

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
    try (SqlSession session = factory.openSession(false)) {
      this.session = session;
      init(session);
      exportCore();
      exportExtensions();
      closeWriter();
      exportMetadata();
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
      num.processTree(datasetKey, null, req.getTaxonID(), req.getExclusions(), req.getMinRank(), req.isSynonyms(), true).forEach(u -> {
        if (u.isTaxon() && taxonIDs.size() < maxUsageIDs) {
          taxonIDs.add(u.getId());
        }
        try {
          write(u);
          writer.next();
        } catch (final IOException e) {
          throw new RuntimeException(e);
        }
      });
    }
  }

  private void exportExtensions() throws IOException {
    exportTaxonExtension(EntityType.VERNACULAR, VernacularNameMapper.class, this::write);
  }

  private boolean individually(){
    return taxonIDs.size() < maxUsageIDs;
  }

  private <T extends SectorScopedEntity<Integer>> void exportTaxonExtension(EntityType entity, Class<? extends TaxonExtensionMapper<T>> mapperClass, ThrowingBiConsumer<String, T, IOException> writer
  ) throws IOException {
    if (newDataFile(define(entity))) {
      try (SqlSession session = factory.openSession()) {
        TaxonExtensionMapper<T> exm = session.getMapper(mapperClass);
        if (individually()) {
          var key = DSID.of(datasetKey, "");
          for (String id : taxonIDs) {
            for (T x : exm.listByTaxon(key.id(id))) {
              writer.accept(id, x);
              this.writer.next();
            }
          }
        } else {
          exm.processDataset(datasetKey).forEach(x -> {
            try {
              writer.accept(x.getTaxonID(), x.getObj());
              this.writer.next();
            } catch (final IOException e) {
              throw new RuntimeException(e);
            }
          });
        }
      }
    }
  }

  private void exportMetadata() {

  }

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

  abstract void write(NameUsageBase u);

  abstract void write(String taxonID, VernacularName vn);

}
