package life.catalogue.importer;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.common.collection.DefaultMap;
import life.catalogue.common.lang.InterruptedRuntimeException;
import life.catalogue.csv.CsvReader;
import life.catalogue.csv.MappingInfos;
import life.catalogue.csv.Schema;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.store.CRUDStore;
import life.catalogue.importer.store.ImportStore;
import life.catalogue.importer.store.model.RelationData;
import life.catalogue.importer.store.model.UsageData;
import life.catalogue.metadata.MetadataFactory;

import org.gbif.dwc.terms.Term;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

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
public abstract class DataCsvInserter implements DataInserter {
  private static final Logger LOG = LoggerFactory.getLogger(DataCsvInserter.class);
  protected static final String INTERRUPT_MESSAGE = "Data inserter interrupted, exit early with incomplete import";
  protected final DatasetSettings settings;
  protected final ImportStore store;
  protected final Path folder;
  protected final CsvReader reader;
  protected final ReferenceFactory refFactory;
  private final Set<Term> interpretedClassTerms = new HashSet<>();
  private int vcounter;
  private Map<Term, AtomicInteger> badTaxonFks = DefaultMap.createCounter();

  protected DataCsvInserter(Path folder, CsvReader reader, ImportStore store, DatasetSettings settings, ReferenceFactory refFactory) {
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
      insert();
      LOG.info("Data insert completed. {} verbatim records processed, {} stored", vcounter, store.verbatimSize());

    } catch (InterruptedRuntimeException e) {
      throw e.asChecked();

    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to batch insert csv data", e);
    }
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

  private void processVerbatim(final CsvReader reader, final Term classTerm, Predicate<VerbatimRecord> proc) {
    runtimeInterruptIfCancelled(INTERRUPT_MESSAGE);
    final AtomicInteger counter = new AtomicInteger(0);
    final AtomicInteger success = new AtomicInteger(0);
    interpretedClassTerms.add(classTerm);
    reader.stream(classTerm).forEach(rec -> {
      runtimeInterruptIfCancelled(INTERRUPT_MESSAGE);
      store.put(rec);
      if (proc.test(rec)) {
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
    LOG.info("Inserted {} verbatim, {} successfully processed {}", counter.get(), success.get(), classTerm.prefixedName());
    vcounter += counter.get();
  }
  
  protected <T extends VerbatimEntity > void insertEntities(final CsvReader reader, final Term classTerm,
                                                           Function<VerbatimRecord, Optional<T>> interpret,
                                                           Predicate<T> add
  ) {
    processVerbatim(reader, classTerm, (rec) -> {
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
                                                                final BiConsumer<UsageData, T> add
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
                                                                final Function<VerbatimRecord, List<T>> interpret,
                                                                final Function<VerbatimRecord, String> idFunc,
                                                                final BiConsumer<UsageData, T> add
  ) {
    processVerbatim(reader, classTerm, (rec) ->   {
      List<T> results = interpret.apply(rec);
      if (reader.isEmpty()) return false;
      boolean interpreted = true;
      for (T obj : results) {
        String id = idFunc.apply(rec);
        UsageData t = store.usages().objByID(id);
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

  protected void insertNameRelations(final CsvReader reader, final Term classTerm,
                                     Function<VerbatimRecord, Optional<RelationData<NomRelType>>> interpret,
                                     Term idTerm, Term relatedIdTerm, Issue invalidIdIssue
  ) {
    insertRelations(reader, classTerm, interpret, rel -> {
      var n = store.names().objByID(rel.getFromID());
      n.relations.add(rel);
      store.names().update(n);
    }, store.names(), idTerm, relatedIdTerm, invalidIdIssue, true);
  }

  protected void insertTaxonSpiRelations(final CsvReader reader, final Term classTerm,
                                     Function<VerbatimRecord, Optional<RelationData<SpeciesInteractionType>>> interpret,
                                     Term idTerm, Term relatedIdTerm, Issue invalidIdIssue
  ) {
    insertRelations(reader, classTerm, interpret, rel -> {
      var u = store.usages().objByID(rel.getFromID());
      u.spiRelations.add(rel);
      if (rel.getToID() == null && rel.getRelatedScientificName() != null) {
        // try to find related usage id by name
        var uids = store.usageIDsByName(rel.getRelatedScientificName(), null, null, true);
        if (uids != null && uids.size() == 1) {
          rel.setToID(uids.iterator().next());
        }
      }
      store.usages().update(u);
    }, store.names(), idTerm, relatedIdTerm, invalidIdIssue, false);
  }

  protected void insertTaxonTCRelations(final CsvReader reader, final Term classTerm,
                                         Function<VerbatimRecord, Optional<RelationData<TaxonConceptRelType>>> interpret,
                                         Term idTerm, Term relatedIdTerm, Issue invalidIdIssue
  ) {
    insertRelations(reader, classTerm, interpret, rel -> {
      var u = store.usages().objByID(rel.getFromID());
      u.tcRelations.add(rel);
      store.usages().update(u);
    }, store.names(), idTerm, relatedIdTerm, invalidIdIssue, true);
  }

  protected <T extends Enum<?>> void insertRelations(final CsvReader reader, final Term classTerm,
                                 Function<VerbatimRecord, Optional<RelationData<T>>> interpret,
                                 final Consumer<RelationData<T>> add,
                                 CRUDStore<?> entityStore, Term idTerm, Term relatedIdTerm,
                                 Issue invalidIdIssue, boolean requireRelatedID
  ) {
    processVerbatim(reader, classTerm,rec -> {
      // ignore any relation pointing to itself!
      String from = rec.getRaw(idTerm);
      String to = rec.getRaw(relatedIdTerm);
      if (from != null && from.equals(to)) {
        rec.add(Issue.SELF_REFERENCED_RELATION);
        return false;
      }

      Optional<RelationData<T>> opt = interpret.apply(rec);
      if (opt.isPresent()) {
        // make sure ids exist
        if (from == null || !entityStore.exists(from)) {
          rec.add(invalidIdIssue);
          return false;
        }
        if (requireRelatedID) {
          if (to == null || !entityStore.exists(to)) {
            rec.add(invalidIdIssue);
            return false;
          }
        } else if (to != null && !entityStore.exists(to)){
          rec.add(invalidIdIssue);
          to = null;
        }
        RelationData<T>rel = opt.get();
        rel.setVerbatimKey(rec.getId());
        rel.setFromID(from);
        rel.setToID(to);
        add.accept(rel);
        return true;
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
        if (store.names().exists(tm.getNameId())) {
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
    processVerbatim(reader, classTerm, (rec) -> {
      Optional<Treatment> opt = interpret.apply(rec);
      if (opt.isPresent()) {
        Treatment t = opt.get();
        var nu = store.usages().objByID(t.getId());
        if (nu == null) {
          rec.add(Issue.TAXON_ID_INVALID);
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

  protected abstract void insert() throws NormalizationFailedException, InterruptedException, InterruptedRuntimeException;

}
