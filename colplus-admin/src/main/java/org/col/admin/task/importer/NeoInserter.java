package org.col.admin.task.importer;

import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.model.Labels;
import org.col.api.model.Dataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Optional;

/**
 *
 */
public abstract class NeoInserter {
  private static final Logger LOG = LoggerFactory.getLogger(NeoInserter.class);

  protected final NeoDb store;
  protected final File folder;
  protected final InsertMetadata meta = new InsertMetadata();

  public NeoInserter(File folder, NeoDb store) {
    this.folder = folder;
    this.store = store;
  }


  public InsertMetadata insertAll() throws NormalizationFailedException {
    store.startBatchMode();
    insert();
    LOG.info("Data insert completed, {} nodes created", meta.getRecords());
    store.endBatchMode();
    LOG.info("Neo batch inserter closed, data flushed to disk", meta.getRecords());

    LOG.info("Start processing explicit relations ...");
    store.process(Labels.ALL,10000, relationProcessor());

    // the key will be preserved by the store
    Optional<Dataset> d = readMetadata();
    d.ifPresent(store::put);

    return meta;
  }

  public abstract void insert() throws NormalizationFailedException;

  protected abstract NeoDb.NodeBatchProcessor relationProcessor();

  protected abstract Optional<Dataset> readMetadata();

}
