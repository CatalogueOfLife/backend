package org.col.admin.importer;

import java.util.concurrent.Callable;
import org.col.admin.config.ImporterConfig;
import org.col.admin.importer.es.EsClientFactory;
import org.col.admin.importer.neo.NeoDb;
import org.elasticsearch.client.Client;

public class EsImport implements Callable<Boolean> {

  private final int datasetKey;
  private final NeoDb store;
  private final ImporterConfig neoCfg;
  private final EsClientFactory esClientFactory;

  public EsImport(int datasetKey, NeoDb store, ImporterConfig neoCfg,
      EsClientFactory esClientFactory) {
    this.datasetKey = datasetKey;
    this.store = store;
    this.neoCfg = neoCfg;
    this.esClientFactory = esClientFactory;
  }

  @Override
  public Boolean call() throws InterruptedException {
    checkIfCancelled();
    insertNames();
    return Boolean.TRUE;
  }

  private void insertNames() {
    try (Client esClient = esClientFactory.getClient()) {

    }
  }

  private static void checkIfCancelled() throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException("EsImport was cancelled/interrupted");
    }
  }

}
