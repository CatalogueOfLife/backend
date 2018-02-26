package org.col.admin.task.importer;

import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.model.Labels;
import org.col.api.model.Dataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;

/**
 *
 */
public abstract class NeoInserter {
  private static final Logger LOG = LoggerFactory.getLogger(NeoInserter.class);

  protected final NeoDb store;
  protected final Path folder;
  protected final InsertMetadata meta = new InsertMetadata();

  public NeoInserter(Path folder, NeoDb store) {
    this.folder = folder;
    this.store = store;
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
    insert();
    LOG.info("Regular insert completed, {} nodes created, total={}", meta.getRecords()-batchRec, meta.getRecords());

    LOG.info("Start processing explicit relations ...");
    store.process(Labels.ALL,10000, relationProcessor());

    return meta;
  }

  public abstract void batchInsert() throws NormalizationFailedException;

  public abstract void insert() throws NormalizationFailedException;

  protected abstract NeoDb.NodeBatchProcessor relationProcessor();

  protected abstract Optional<Dataset> readMetadata();

}
