package org.col.commands.importer;

import org.col.commands.importer.neo.NormalizerInserter;
import org.col.commands.importer.neo.NormalizerStore;
import org.col.commands.importer.neo.NotUniqueRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 *
 */
public class Normalizer implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(Normalizer.class);

  private final File dwca;
  private final NormalizerStore store;

  public Normalizer(NormalizerStore store, File dwca) {
    this.dwca = dwca;
    this.store = store;
  }

  /**
   * Run the normalizer and closes the store at the end.
   *
   * @throws NormalizationFailedException
   */
  @Override
  public void run() throws NormalizationFailedException {
    run(true);
  }

  /**
   * Run the normalizer.
   *
   * @param closeStore Should the store be closed after running or on exception?
   * @throws NormalizationFailedException
   */
  public void run(boolean closeStore) throws NormalizationFailedException {
    LOG.info("Start normalization of {}", store);
    try {
      // batch import verbatim records its own batchdb
      insertData();
      // insert normalizer db relations, create implicit nodes if needed and parse names
      normalize();
      // matches names and taxon concepts and builds metrics per name/taxon
      matchAndCount();
      LOG.info("Normalization succeeded");
    } finally {
      if (closeStore) {
        store.close();
        LOG.info("Normalizer store shut down");
      }
    }
  }

  private void matchAndCount() {

  }

  private void normalize() {

  }

  private void insertData() throws NormalizationFailedException {
    // closing the batch inserter opens the normalizer db again for regular access via the store
    try {
      NormalizerInserter inserter = new NormalizerInserter(store);
      inserter.insert(dwca);

    } catch (NotUniqueRuntimeException e) {
      throw new NormalizationFailedException(e.getProperty() + " values not unique: " + e.getKey(), e);

    } catch (IOException e) {
      throw new NormalizationFailedException("IO error: " + e.getMessage(), e);
    }
  }
}