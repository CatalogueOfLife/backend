package life.catalogue.importer;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.Setting;
import life.catalogue.common.collection.DefaultMap;
import life.catalogue.common.lang.InterruptedRuntimeException;
import life.catalogue.csv.CsvReader;
import life.catalogue.csv.MappingInfos;
import life.catalogue.csv.Schema;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.neo.NeoCRUDStore;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.model.NeoRel;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.metadata.MetadataFactory;

import org.gbif.dwc.terms.Term;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.univocity.parsers.common.CommonParserSettings;
import com.univocity.parsers.csv.CsvFormat;

import static life.catalogue.common.lang.Exceptions.interruptIfCancelled;
import static life.catalogue.common.lang.Exceptions.runtimeInterruptIfCancelled;

/**
 * The base class for all inserters using CSV based data.
 * As the insertion can be a longer running and blocking process, potentially kicking off child threads, e.g. name parsing jobs,
 * we check for interrupted threads frequently to allow users to cancel the job. As we make heavy use of streams and functional programming
 * all methods are free to throw the InterruptedRuntimeException instead of the checked version.
 */
public abstract class NeoCsvInserter implements NeoInserter {
  private static final Logger LOG = LoggerFactory.getLogger(NeoCsvInserter.class);
  protected static final String INTERRUPT_MESSAGE = "NeoInserter interrupted, exit early with incomplete import";
  protected final DatasetSettings settings;
  protected final NeoDb store;
  protected final Path folder;
  protected final CsvReader reader;
  protected final ReferenceFactory refFactory;
  private final Set<Term> interpretedClassTerms = new HashSet<>();
  private int vcounter;
  private Map<Term, AtomicInteger> badTaxonFks = DefaultMap.createCounter();

  protected NeoCsvInserter(Path folder, CsvReader reader, NeoDb store, DatasetSettings settings, ReferenceFactory refFactory) {
    this.folder = folder;
    this.reader = reader;
    this.store = store;
    this.settings = settings;
    this.refFactory = refFactory;
    // update CSV reader with manual dataset settings if existing
    // see https://github.com/Sp2000/colplus-backend/issues/582
    if (settings.has(Setting.CSV_DELIMITER) || settings.has(Setting.CSV_QUOTE) || settings.has(Setting.CSV_QUOTE_ESCAPE)) {
      boolean isTsv = Objects.equals(settings.getChar(Setting.CSV_DELIMITER), '\t')  && !settings.has(Setting.CSV_QUOTE);
      for (Schema s : reader.schemas()) {
        if (isTsv) {
          if (!s.isTsv()) {
            updateSchemaSettings(s, CsvReader.tsvSetting());
          }

        } else {
          CsvFormat csv;
          if (s.isTsv()) {
            var set = CsvReader.csvSetting();
            updateSchemaSettings(s, set);
            csv = set.getFormat();
          } else {
            csv = (CsvFormat) s.settings.getFormat();
          }

          setChar(Setting.CSV_DELIMITER, csv::setDelimiter);
          setChar(Setting.CSV_QUOTE, csv::setQuote);
          setChar(Setting.CSV_QUOTE_ESCAPE, csv::setQuoteEscape);
        }
      }
    }
  }

  private void updateSchemaSettings(Schema old, CommonParserSettings<?> settings) {
    settings.setNumberOfRowsToSkip(old.settings.getNumberOfRowsToSkip());
    Schema s2 = new Schema(old.files, old.rowType, old.encoding, settings, old.columns);
    reader.updateSchema(s2);
  }

  private void setChar(Setting key, Consumer<Character> setter) {
    if (settings.has(key)) {
      setter.accept(settings.getChar(key));
    }
  }

  /**
   * @throws NormalizationFailedException
   * @throws InterruptedException InterruptedRuntimeException thrown by any processing are converted into regular InterruptedException by this method
   */
  @Override
  public final void insertAll() throws NormalizationFailedException, InterruptedException {
    try {
      interruptIfCancelled(INTERRUPT_MESSAGE);
      batchInsert();
      LOG.info("Batch insert completed, {} verbatim records processed, {} nodes created", vcounter, store.size());

    } catch (InterruptedRuntimeException e) {
      throw e.asChecked();

    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to batch insert csv data", e);
    }

    final int batchV = vcounter;
    final int batchRec = store.size();
    interruptIfCancelled(INTERRUPT_MESSAGE);
    postBatchInsert();
    LOG.info("Post batch insert completed, {} verbatim records processed creating {} new nodes", batchV, store.size() - batchRec);
    
    interruptIfCancelled(INTERRUPT_MESSAGE);
    LOG.debug("Start processing explicit relations ...");
    store.process(null,relationProcessor());

    LOG.info("Insert of {} verbatim records and {} nodes completed", vcounter, store.size());
  }

  private void processVerbatimOnly(final CsvReader reader, final Term classTerm) {
    runtimeInterruptIfCancelled(INTERRUPT_MESSAGE);
    final AtomicInteger counter = new AtomicInteger(0);
    reader.stream(classTerm).forEach(rec -> {
      runtimeInterruptIfCancelled(INTERRUPT_MESSAGE);
      rec.add(Issue.NOT_INTERPRETED);
      store.put(rec);
      counter.incrementAndGet();
    });
    interpretedClassTerms.add(classTerm);
    LOG.info("Inserted {} verbatim records of {}", counter.get(), classTerm.prefixedName());
    vcounter += counter.get();
  }

  private void processVerbatim(final CsvReader reader, final Term classTerm, BiPredicate<VerbatimRecord, Transaction> proc) {
    runtimeInterruptIfCancelled(INTERRUPT_MESSAGE);
    final AtomicInteger counter = new AtomicInteger(0);
    final AtomicInteger success = new AtomicInteger(0);
    interpretedClassTerms.add(classTerm);
    try (var tx = store.beginTx()) {
      reader.stream(classTerm).forEach(rec -> {
        runtimeInterruptIfCancelled(INTERRUPT_MESSAGE);
        store.put(rec);
        if (proc.test(rec, tx)) {
          success.incrementAndGet();
        } else {
          rec.add(Issue.NOT_INTERPRETED);
        }
        // processing might already have flagged issues, load and merge them
        VerbatimRecord old = store.getVerbatim(rec.getId());
        rec.add(old.getIssues());
        store.put(rec);
        counter.incrementAndGet();
      });
      tx.commit();
    }
    LOG.info("Inserted {} verbatim, {} successfully processed {}", counter.get(), success.get(), classTerm.prefixedName());
    vcounter += counter.get();
  }
  
  protected <T extends VerbatimEntity > void insertEntities(final CsvReader reader, final Term classTerm,
                                                           BiFunction<VerbatimRecord, Transaction, Optional<T>> interpret,
                                                           BiPredicate<T, Transaction> add
  ) {
    processVerbatim(reader, classTerm, (rec, tx) -> {
      Optional<T> opt = interpret.apply(rec, tx);
      if (opt.isPresent()) {
        T obj = opt.get();
        obj.setVerbatimKey(rec.getId());
        return add.test(obj, tx);
      }
      return false;
    });
  }

  protected <T extends VerbatimEntity> void insertTaxonEntities(final CsvReader reader, final Term classTerm,
                                                                final BiFunction<VerbatimRecord, Transaction, List<T>> interpret,
                                                                final Term taxonIdTerm,
                                                                final BiConsumer<NeoUsage, T> add
  ) {
    insertTaxonEntities(reader, classTerm, interpret, v -> v.getRaw(taxonIdTerm), add);
  }

  protected void insertVerbatimEntities(CsvReader reader, final Term... classTerms) {
    if (classTerms != null) {
      for (Term classTerm : classTerms) {
        if (interpretedClassTerms.contains(classTerm)) {
          LOG.info("{} has already been inserted", classTerm.prefixedName());
          continue;
        }
        processVerbatimOnly(reader, classTerm);
      }
    }
  }

  protected <T extends VerbatimEntity> void insertTaxonEntities(final CsvReader reader, final Term classTerm,
                                                                final BiFunction<VerbatimRecord, Transaction, List<T>> interpret,
                                                                final Function<VerbatimRecord, String> idFunc,
                                                                final BiConsumer<NeoUsage, T> add
  ) {
    processVerbatim(reader, classTerm, (rec,tx) ->   {
      List<T> results = interpret.apply(rec, tx);
      if (reader.isEmpty()) return false;
      boolean interpreted = true;
      for (T obj : results) {
        String id = idFunc.apply(rec);
        NeoUsage t = store.usages().objByID(id, tx);
        if (t != null) {
          obj.setVerbatimKey(rec.getId());
          add.accept(t, obj);
          store.usages().update(t, tx);
        } else {
          interpreted = false;
          badTaxonFks.get(rec.getType()).incrementAndGet();
          LOG.warn("Non existing taxonID {} found in {} record line {}, {}", id, rec.getType().simpleName(), rec.getLine(), rec.getFile());
        }
      }
      return interpreted;
    });
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
                                 Issue invalidIdIssue, boolean requireRelatedID
  ) {
    insertRelations(reader, classTerm, interpret, entityStore, v -> v.getRaw(idTerm), relatedIdTerm, invalidIdIssue, requireRelatedID);
  }

  protected void insertRelations(final CsvReader reader, final Term classTerm,
                                 Function<VerbatimRecord, Optional<NeoRel>> interpret,
                                 NeoCRUDStore<?> entityStore, Function<VerbatimRecord, String> idFunc, Term relatedIdTerm,
                                 Issue invalidIdIssue, boolean requireRelatedID
  ) {
    processVerbatim(reader, classTerm, (rec,tx) -> {
      // ignore any relation pointing to itself!
      String from = idFunc.apply(rec);
      String to = rec.getRaw(relatedIdTerm);
      if (from != null && from.equals(to)) {
        rec.add(Issue.SELF_REFERENCED_RELATION);
        return false;
      }
      Optional<NeoRel> opt = interpret.apply(rec);
      if (opt.isPresent()) {
        Node n1 = entityStore.nodeByID(from, tx);
        Node n2 = entityStore.nodeByID(to, tx);
        if (n2 == null && !requireRelatedID) {
          n2 = store.getDevNullNode();
        }
        if (n1 != null && n2 != null) {
          NeoRel rel = opt.get();
          rel.setVerbatimKey(rec.getId());
          store.createNeoRel(n1, n2, rel);
          return true;
        }
        rec.add(invalidIdIssue);
      }
      return false;
    });
  }

  protected void interpretTypeMaterial(final CsvReader reader, final Term classTerm,
                                     Function<VerbatimRecord, Optional<TypeMaterial>> interpret
  ) {
    processVerbatim(reader, classTerm, (rec,tx) -> {
      Optional<TypeMaterial> opt = interpret.apply(rec);
      if (opt.isPresent()) {
        TypeMaterial tm = opt.get();
        tm.setVerbatimKey(rec.getId());
        Node n = store.names().nodeByID(tm.getNameId(), tx);
        if (n != null) {
          store.typeMaterial().create(tm);
          return true;
        }
        rec.add(Issue.NAME_ID_INVALID);
      }
      return false;
    });
  }

  protected void interpretTreatment(final CsvReader reader, final Term classTerm,
                                       Function<VerbatimRecord, Optional<Treatment>> interpret
  ) {
    processVerbatim(reader, classTerm, (rec,tx) -> {
      Optional<Treatment> opt = interpret.apply(rec);
      if (opt.isPresent()) {
        Treatment t = opt.get();
        var nu = store.usages().objByID(t.getId(), tx);
        if (nu == null) {
          rec.add(Issue.TAXON_ID_INVALID);
        } else {
          t.setVerbatimKey(rec.getId());
          nu.treatment = t;
          store.usages().update(nu, tx);
          return true;
        }
      }
      return false;
    });
  }

  /**
   * Reads all kind of metadata with preference of metadata.yaml > metadata.json > eml.xml
   */
  @Override
  public Optional<DatasetWithSettings> readMetadata() {
    return MetadataFactory.readMetadata(folder);
  }

  @Override
  public MappingInfos getMappingFlags() {
    return reader.getMappingFlags();
  }

  @Override
  public Optional<Path> logo() {
    return reader.logo();
  }

  protected abstract void batchInsert() throws NormalizationFailedException, InterruptedException, InterruptedRuntimeException;
  
  protected void postBatchInsert() throws NormalizationFailedException, InterruptedException {
    // nothing by default
  }
  
  protected abstract BiConsumer<Node, Transaction> relationProcessor();

}
