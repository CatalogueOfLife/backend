package org.col.admin.task.importer;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.col.admin.task.importer.dwca.DwcaReader;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.model.Labels;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.admin.task.importer.reference.ReferenceFactory;
import org.col.api.model.*;
import org.col.api.vocab.Issue;
import org.col.csv.CsvReader;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public abstract class NeoInserter {
  private static final Logger LOG = LoggerFactory.getLogger(NeoInserter.class);

  protected final NeoDb store;
  protected final Path folder;
  protected final InsertMetadata meta = new InsertMetadata();
  protected final ReferenceFactory refFactory;

  public NeoInserter(Path folder, NeoDb store, ReferenceFactory refFactory) {
    this.folder = folder;
    this.store = store;
    this.refFactory = refFactory;
  }

  public InsertMetadata insertAll() throws NormalizationFailedException {
    // the key will be preserved by the store
    Optional<Dataset> d = readMetadata();
    d.ifPresent(store::put);

    store.startBatchMode();
    batchInsert();
    LOG.info("Batch insert completed, {} nodes created", meta.getRecords());

    store.endBatchMode();
    LOG.info("Neo batch inserter closed, data flushed to disk", meta.getRecords());

    final int batchRec = meta.getRecords();
    postBatchInsert();
    LOG.info("Regular insert completed, {} nodes created, total={}", meta.getRecords()-batchRec, meta.getRecords());

    LOG.info("Start processing explicit relations ...");
    store.process(Labels.ALL,10000, relationProcessor());

    return meta;
  }

  protected <T extends VerbatimEntity> void insertEntities(final CsvReader reader, final Term classTerm,
                                                           Function<TermRecord, Optional<T>> interpret,
                                                           Consumer<T> add
  ) {
    final AtomicInteger counter = new AtomicInteger(0);
    final AtomicInteger added = new AtomicInteger(0);
    reader.stream(classTerm).forEach(rec -> {
      store.assignKey(rec);
      Optional<T> opt = interpret.apply(rec);
      if (opt.isPresent()) {
        T obj = opt.get();
        obj.setVerbatimKey(rec.getKey());
        add.accept(obj);
        added.incrementAndGet();
      } else {
        rec.addIssue(Issue.NOT_INTERPRETED);
      }
      store.put(rec);
      counter.incrementAndGet();
    });
    LOG.info("Inserted {} verbatim, {} added {}", counter.get(), added.get(), classTerm.prefixedName());
  }

  protected <T extends VerbatimEntity> void insertTaxonEntities(final CsvReader reader, final Term classTerm,
                                                                Function<TermRecord, List<T>> interpret,
                                                                Term taxonIdTerm,
                                                                BiConsumer<NeoTaxon, T> add
  ) {
    final AtomicInteger counter = new AtomicInteger(0);
    final AtomicInteger added = new AtomicInteger(0);
    reader.stream(classTerm).forEach(rec -> {
      store.assignKey(rec);
      interpret.apply(rec).forEach(obj -> {
        obj.setVerbatimKey(rec.getKey());

        String id = rec.getRaw(taxonIdTerm);
        NeoTaxon t = store.getByID(id);
        if (t != null) {
          add.accept(t, obj);
          store.update(t);
          added.incrementAndGet();
        } else {
          LOG.warn("Non existing {} {} found in {} record line {}, {}", taxonIdTerm.simpleName(), id, rec.getType().simpleName(), rec.getLine(), rec.getFile());
          //TODO: log issue in verbatim record and persist it!
        }
      });
      store.put(rec);
      counter.incrementAndGet();
    });
    LOG.info("Inserted {} verbatim, {} interpreted {}", counter.get(), added.get(), classTerm.prefixedName());
  }

  public abstract void batchInsert() throws NormalizationFailedException;

  public abstract void postBatchInsert() throws NormalizationFailedException;

  protected abstract NeoDb.NodeBatchProcessor relationProcessor();

  protected abstract Optional<Dataset> readMetadata();

}
