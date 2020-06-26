package life.catalogue.importer;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.TypeMaterial;
import life.catalogue.api.model.VerbatimEntity;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.Setting;
import life.catalogue.common.collection.DefaultMap;
import life.catalogue.common.lang.InterruptedRuntimeException;
import life.catalogue.csv.CsvReader;
import life.catalogue.csv.Schema;
import life.catalogue.importer.neo.NeoCRUDStore;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NodeBatchProcessor;
import life.catalogue.importer.neo.model.NeoRel;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.reference.ReferenceFactory;
import org.gbif.dwc.terms.Term;
import org.neo4j.graphdb.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public abstract class NeoCsvInserter implements NeoInserter {
  private static final Logger LOG = LoggerFactory.getLogger(NeoCsvInserter.class);

  protected final DatasetSettings settings;
  protected final NeoDb store;
  protected final Path folder;
  protected final CsvReader reader;
  protected final ReferenceFactory refFactory;
  private int vcounter;
  private Map<Term, AtomicInteger> badTaxonFks = DefaultMap.createCounter();
  
  
  public NeoCsvInserter(Path folder, CsvReader reader, NeoDb store, DatasetSettings settings, ReferenceFactory refFactory) {
    this.folder = folder;
    this.reader = reader;
    this.store = store;
    this.settings = settings;
    this.refFactory = refFactory;
    // update CSV reader with manual dataset settings if existing
    // see https://github.com/Sp2000/colplus-backend/issues/582
    for (Schema s : reader.schemas()) {
      setChar(Setting.CSV_DELIMITER, s.settings.getFormat()::setDelimiter);
      setChar(Setting.CSV_QUOTE, s.settings.getFormat()::setQuote);
      setChar(Setting.CSV_QUOTE_ESCAPE, s.settings.getFormat()::setQuoteEscape);
    }
  }

  private void setChar(Setting key, Consumer<Character> setter) {
    if (settings.has(key)) {
      String val = settings.getString(key);
      if (!val.isEmpty()) {
        if (val.length() != 1) {
          LOG.warn("Setting {} must be a single character but got >>{}<< instead. Ignore setting", key, val);
        } else {
          setter.accept(val.charAt(0));
        }
      }
    }
  }

  @Override
  public final void insertAll() throws NormalizationFailedException {
    store.startBatchMode();
    interruptIfCancelled("Normalizer interrupted, exit early");
    try {
      batchInsert();
      LOG.info("Batch insert completed, {} verbatim records processed, {} nodes created", vcounter, store.size());
    } catch (InterruptedRuntimeException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to batch insert csv data", e);
    }

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
  
  @Override
  public void reportBadFks() {
    for (Map.Entry<Term, AtomicInteger> entry : badTaxonFks.entrySet()) {
      LOG.warn("The inserted dataset contains {} bad taxon foreign keys in {}", entry.getValue(), entry.getKey().prefixedName());
    }
  }
  
  protected void insertRelations(final CsvReader reader, final Term classTerm,
                                 Function<VerbatimRecord, Optional<NeoRel>> interpret,
                                 NeoCRUDStore<?> entityStore, Term idTerm, Term relatedIdTerm,
                                 Issue invalidIdIssue
  ) {
    processVerbatim(reader, classTerm, rec -> {
      Optional<NeoRel> opt = interpret.apply(rec);
      if (opt.isPresent()) {
        Node n1 = entityStore.nodeByID(rec.getRaw(idTerm));
        Node n2 = entityStore.nodeByID(rec.getRaw(relatedIdTerm));
        if (n1 != null && n2 != null) {
          store.createNeoRel(n1, n2, opt.get());
          return true;
        }
        rec.addIssue(invalidIdIssue);
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

  @Override
  public MappingFlags getMappingFlags() {
    return reader.getMappingFlags();
  }

  @Override
  public Optional<Path> logo() {
    return reader.logo();
  }

  protected abstract void batchInsert() throws NormalizationFailedException;
  
  protected void postBatchInsert() throws NormalizationFailedException {
    // nothing by default
  }
  
  protected abstract NodeBatchProcessor relationProcessor();

}
