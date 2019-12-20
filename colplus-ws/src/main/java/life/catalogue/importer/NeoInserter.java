package life.catalogue.importer;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.TypeMaterial;
import life.catalogue.api.model.VerbatimEntity;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.DatasetSettings;
import life.catalogue.api.vocab.Issue;
import life.catalogue.common.collection.DefaultMap;
import life.catalogue.csv.CsvReader;
import life.catalogue.csv.Schema;
import life.catalogue.img.ImageService;
import life.catalogue.img.ImageServiceFS;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NodeBatchProcessor;
import life.catalogue.importer.neo.model.NeoNameRel;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.reference.ReferenceFactory;
import org.gbif.dwc.terms.Term;
import org.neo4j.graphdb.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static life.catalogue.common.lang.Exceptions.interruptIfCancelled;

/**
 *
 */
public abstract class NeoInserter {
  private static final Logger LOG = LoggerFactory.getLogger(NeoInserter.class);
  
  protected final NeoDb store;
  protected final Path folder;
  protected final CsvReader reader;
  protected final ReferenceFactory refFactory;
  protected final ImageService imgService;
  private int vcounter;
  private Map<Term, AtomicInteger> badTaxonFks = DefaultMap.createCounter();
  
  
  public NeoInserter(Path folder, CsvReader reader, NeoDb store, ReferenceFactory refFactory, ImageService imgService) {
    this.folder = folder;
    this.reader = reader;
    this.store = store;
    this.refFactory = refFactory;
    this.imgService = imgService;
    // update CSV reader with manual dataset settings if existing
    // see https://github.com/Sp2000/colplus-backend/issues/582
    for (Schema s : reader.schemas()) {
      setChar(DatasetSettings.CSV_DELIMITER, s.settings.getFormat()::setDelimiter);
      setChar(DatasetSettings.CSV_QUOTE, s.settings.getFormat()::setQuote);
      setChar(DatasetSettings.CSV_QUOTE_ESCAPE, s.settings.getFormat()::setQuoteEscape);
    }
  }

  private void setChar(DatasetSettings key, Consumer<Character> setter) {
    if (store.getDataset().hasSetting(key)) {
      String val = store.getDataset().getSetting(key);
      if (!val.isEmpty()) {
        if (val.length() != 1) {
          LOG.warn("Setting {} must be a single character but got >>{}<< instead. Ignore setting", key, val);
        } else {
          setter.accept(val.charAt(0));
        }
      }
    }
  }

  public final void insertAll() throws NormalizationFailedException {
    // the key will be preserved by the store
    Optional<Dataset> d = readMetadata();
    d.ifPresent(store::put);
  
    // lookout for local logo file if we do not have a URL
      if (!d.isPresent() || d.get().getLogo() == null) {
        reader.logo().ifPresent(l -> {
          try {
            imgService.putDatasetLogo(store.getDataset().getKey(), ImageServiceFS.read(Files.newInputStream(l)));
          } catch (IOException e) {
            LOG.warn("Failed to read local logo file {}", l);
          }
        });
      }
  
    store.startBatchMode();
    interruptIfCancelled("Normalizer interrupted, exit early");
    batchInsert();
    LOG.info("Batch insert completed, {} verbatim records processed, {} nodes created", vcounter, store.size());
  
    interruptIfCancelled("Normalizer interrupted, exit early");
    store.endBatchMode();
    LOG.info("Neo batch inserter closed, data flushed to disk");
  
    final int batchV = vcounter;
    final int batchRec = store.size();
    interruptIfCancelled("Normalizer interrupted, exit early");
    postBatchInsert();
    LOG.info("Post batch insert completed, {} verbatim records processed creating {} new nodes", batchV, store.size() - batchRec);
    
    interruptIfCancelled("Normalizer interrupted, exit early");
    LOG.debug("Start processing explicit relations ...");
    store.process(null,5000, relationProcessor());

    LOG.info("Insert of {} verbatim records and {} nodes completed", vcounter, store.size());
  }
  
  private void processVerbatim(final CsvReader reader, final Term classTerm, Function<VerbatimRecord, Boolean> proc) {
    interruptIfCancelled("NeoInserter interrupted, exit early with incomplete import");
    final AtomicInteger counter = new AtomicInteger(0);
    final AtomicInteger success = new AtomicInteger(0);
    reader.stream(classTerm).forEach(rec -> {
      store.put(rec);
      if (proc.apply(rec)) {
        success.incrementAndGet();
      } else {
        rec.addIssue(Issue.NOT_INTERPRETED);
      }
      // processing might already have flagged issues, load and merge them
      VerbatimRecord old = store.getVerbatim(rec.getId());
      rec.addIssues(old.getIssues());
      store.put(rec);
      counter.incrementAndGet();
    });
    LOG.info("Inserted {} verbatim, {} successfully processed {}", counter.get(), success.get(), classTerm.prefixedName());
    vcounter += counter.get();
  }
  
  protected <T extends VerbatimEntity> void insertEntities(final CsvReader reader, final Term classTerm,
                                                           Function<VerbatimRecord, Optional<T>> interpret,
                                                           Function<T, Boolean> add
  ) {
    processVerbatim(reader, classTerm, rec -> {
      interruptIfCancelled("NeoInserter interrupted, exit early");
      Optional<T> opt = interpret.apply(rec);
      if (opt.isPresent()) {
        T obj = opt.get();
        obj.setVerbatimKey(rec.getId());
        return add.apply(obj);
      }
      return false;
    });
  }
  
  protected <T extends VerbatimEntity> void insertTaxonEntities(final CsvReader reader, final Term classTerm,
                                                                final Function<VerbatimRecord, List<T>> interpret,
                                                                final Term taxonIdTerm,
                                                                final BiConsumer<NeoUsage, T> add
  ) {
    processVerbatim(reader, classTerm, rec -> {
      interruptIfCancelled("NeoInserter interrupted, exit early");
      List<T> results = interpret.apply(rec);
      if (reader.isEmpty()) return false;
      boolean interpreted = true;
      for (T obj : results) {
        String id = rec.getRaw(taxonIdTerm);
        NeoUsage t = store.usages().objByID(id);
        if (t != null) {
          add.accept(t, obj);
          store.usages().update(t);
        } else {
          interpreted = false;
          badTaxonFk(rec, taxonIdTerm, id);
        }
      }
      return interpreted;
    });
  }
  
  private void badTaxonFk(VerbatimRecord rec, Term taxonIdTerm, String id){
    badTaxonFks.get(rec.getType()).incrementAndGet();
    LOG.warn("Non existing {} {} found in {} record line {}, {}", taxonIdTerm.simpleName(), id, rec.getType().simpleName(), rec.getLine(), rec.getFile());
  }
  
  public void reportBadFks() {
    for (Map.Entry<Term, AtomicInteger> entry : badTaxonFks.entrySet()) {
      LOG.warn("The inserted dataset contains {} bad taxon foreign keys in {}", entry.getValue(), entry.getKey().prefixedName());
    }
  }
  
  protected void insertNameRelations(final CsvReader reader, final Term classTerm,
                                     Function<VerbatimRecord, Optional<NeoNameRel>> interpret,
                                     Term nameIdTerm, Term relatedNameIdTerm
  ) {
    processVerbatim(reader, classTerm, rec -> {
      Optional<NeoNameRel> opt = interpret.apply(rec);
      if (opt.isPresent()) {
        Node n1 = store.names().nodeByID(rec.getRaw(nameIdTerm));
        Node n2 = store.names().nodeByID(rec.getRaw(relatedNameIdTerm));
        if (n1 != null && n2 != null) {
          store.createNameRel(n1, n2, opt.get());
          return true;
        }
        rec.addIssue(Issue.NAME_ID_INVALID);
      }
      return false;
    });
  }

  protected void interpretTypeMaterial(final CsvReader reader, final Term classTerm,
                                     Function<VerbatimRecord, Optional<TypeMaterial>> interpret
  ) {
    processVerbatim(reader, classTerm, rec -> {
      Optional<TypeMaterial> opt = interpret.apply(rec);
      if (opt.isPresent()) {
        TypeMaterial tm = opt.get();
        Node n = store.names().nodeByID(tm.getNameId());
        if (n != null) {
          store.typeMaterial().create(tm);
          return true;
        }
        rec.addIssue(Issue.NAME_ID_INVALID);
      }
      return false;
    });
  }

  protected abstract void batchInsert() throws NormalizationFailedException;
  
  protected void postBatchInsert() throws NormalizationFailedException {
    // nothing by default
  }
  
  protected abstract NodeBatchProcessor relationProcessor();
  
  public abstract Optional<Dataset> readMetadata();
  
}
