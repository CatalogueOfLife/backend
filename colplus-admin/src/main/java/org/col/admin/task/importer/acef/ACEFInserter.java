package org.col.admin.task.importer.acef;

import org.col.admin.task.importer.NeoInserter;
import org.col.admin.task.importer.NormalizationFailedException;
import org.col.admin.task.importer.dwca.InsertMetadata;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.api.model.Dataset;
import org.col.api.vocab.DataFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 *
 */
public class ACEFInserter implements NeoInserter {

  private static final Logger LOG = LoggerFactory.getLogger(ACEFInserter.class);

  private final NeoDb store;
  private InsertMetadata meta = new InsertMetadata();

  public ACEFInserter(NeoDb store) throws IOException {
    this.store = store;
  }

  /**
   * Inserts DwC-A data from the source files into the normalizer store using
   * a neo4j batch inserter. Finally indices are build and a regular store instance returned.
   *
   * Before inserting it does a quick check to see if all required dwc terms are actually mapped.
   */
  @Override
  public InsertMetadata insert(File dwca) throws NormalizationFailedException {
    store.startBatchMode();
    //TODO: add data
    LOG.info("Data insert completed, {} nodes created", meta.getRecords());
    store.endBatchMode();
    LOG.info("Neo batch inserter closed, data flushed to disk. Opening regular normalizer db again ...", meta.getRecords());
    updateMetadata();
    return meta;
  }

  /**
   * Reads the dataset metadata and puts it into the store
   */
  private void updateMetadata() {
    Dataset d = new Dataset();
    d.setDataFormat(DataFormat.ACEF);
    try {
      LOG.info("No dataset metadata available");
    } catch (Throwable e) {
      LOG.error("Unable to read dataset metadata from dwc archive", e.getMessage());

    } finally {
      // the key will be preserved by the store
      store.put(d);
    }
  }

}
