package life.catalogue.importer;

import com.univocity.parsers.common.CommonParserSettings;
import com.univocity.parsers.csv.CsvFormat;

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
import life.catalogue.importer.neo.NodeBatchProcessor;
import life.catalogue.importer.neo.model.NeoRel;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.metadata.MetadataFactory;

import org.gbif.dwc.terms.Term;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.neo4j.graphdb.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private static final String INTERRUPT_MESSAGE = "NeoInserter interrupted, exit early with incomplete import";

  protected final DatasetSettings settings;
  protected final NeoDb store;
  protected final Path folder;
  protected final CsvReader reader;
  protected final ReferenceFactory refFactory;
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
      boolean isTsv = '\t' == settings.getChar(Setting.CSV_DELIMITER) && !settings.has(Setting.CSV_QUOTE);
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
      store.startBatchMode();
      batchInsert();
      LOG.info("Batch insert completed, {} verbatim records processed, {} nodes created", vcounter, store.size());

    } catch (InterruptedRuntimeException e) {
      throw e.asChecked();

    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to batch insert csv data", e);

    } finally {
      if (store.isBatchMode()) {
        store.endBatchMode();
      }
    }

    final int batchV = vcounter;
    final int batchRec = store.size();
    interruptIfCancelled(INTERRUPT_MESSAGE);
    postBatchInsert();
    LOG.info("Post batch insert completed, {} verbatim records processed creating {} new nodes", batchV, store.size() - batchRec);
    
    interruptIfCancelled(INTERRUPT_MESSAGE);
    LOG.debug("Start processing explicit relations ...");
    store.process(null,5000, relationProcessor());

    LOG.info("Insert of {} verbatim records and {} nodes completed", vcounter, store.size());
  }

  private void processVerbatim(final CsvReader reader, final Term classTerm, Predicate<VerbatimRecord> proc) {
    runtimeInterruptIfCancelled(INTERRUPT_MESSAGE);
    final AtomicInteger counter = new AtomicInteger(0);
    final AtomicInteger success = new AtomicInteger(0);
    reader.stream(classTerm).forEach(rec -> {
      runtimeInterruptIfCancelled(INTERRUPT_MESSAGE);
      store.put(rec);
      if (proc.test(rec)) {
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
  
  protected <T extends VerbatimEntity > void insertEntities(final CsvReader reader, final Term classTerm,
                                                           Function<VerbatimRecord, Optional<T>> interpret,
                                                           Predicate<T> add
  ) {
    processVerbatim(reader, classTerm, rec -> {
      Optional<T> opt = interpret.apply(rec);
      if (opt.isPresent()) {
        T obj = opt.get();
        obj.setVerbatimKey(rec.getId());
        return add.test(obj);
      }
      return false;
    });
  }

  protected <T extends VerbatimEntity> void insertTaxonEntities(final CsvReader reader, final Term classTerm,
                                                                final Function<VerbatimRecord, List<T>> interpret,
                                                                final Term taxonIdTerm,
                                                                final BiConsumer<NeoUsage, T> add
  ) {
    insertTaxonEntities(reader, classTerm, interpret, v -> v.getRaw(taxonIdTerm), add);
  }

  protected <T extends VerbatimEntity> void insertTaxonEntities(final CsvReader reader, final Term classTerm,
                                                                final Function<VerbatimRecord, List<T>> interpret,
                                                                final Function<VerbatimRecord, String> idFunc,
                                                                final BiConsumer<NeoUsage, T> add
  ) {
    processVerbatim(reader, classTerm, rec ->   {
      List<T> results = interpret.apply(rec);
      if (reader.isEmpty()) return false;
      boolean interpreted = true;
      for (T obj : results) {
        String id = idFunc.apply(rec);
        NeoUsage t = store.usages().objByID(id);
        if (t != null) {
          obj.setVerbatimKey(rec.getId());
          add.accept(t, obj);
          store.usages().update(t);
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
    processVerbatim(reader, classTerm, rec -> {
      // ignore any relation pointing to itself!
      String from = idFunc.apply(rec);
      String to = rec.getRaw(relatedIdTerm);
      if (from != null && from.equals(to)) {
        rec.addIssue(Issue.SELF_REFERENCED_RELATION);
        return false;
      }
      Optional<NeoRel> opt = interpret.apply(rec);
      if (opt.isPresent()) {
        Node n1 = entityStore.nodeByID(from);
        Node n2 = entityStore.nodeByID(to);
        if (n2 == null && !requireRelatedID) {
          n2 = store.getDevNullNode();
        }
        if (n1 != null && n2 != null) {
          NeoRel rel = opt.get();
          rel.setVerbatimKey(rec.getId());
          store.createNeoRel(n1, n2, rel);
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
        tm.setVerbatimKey(rec.getId());
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

  protected void interpretTreatment(final CsvReader reader, final Term classTerm,
                                       Function<VerbatimRecord, Optional<Treatment>> interpret
  ) {
    processVerbatim(reader, classTerm, rec -> {
      Optional<Treatment> opt = interpret.apply(rec);
      if (opt.isPresent()) {
        Treatment t = opt.get();
        var nu = store.usages().objByID(t.getId());
        if (nu == null) {
          rec.addIssue(Issue.TAXON_ID_INVALID);
        } else {
          t.setVerbatimKey(rec.getId());
          nu.treatment = t;
          store.usages().update(nu);
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
  
  protected abstract NodeBatchProcessor relationProcessor();

}
