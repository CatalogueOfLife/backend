package org.col.commands.importer.neo;

import org.col.api.Dataset;
import org.col.api.Reference;
import org.col.api.vocab.Rank;
import org.col.commands.importer.neo.model.NeoTaxon;
import org.col.common.AutoCloseableRuntime;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;

import java.util.List;

/**
 *
 */
public interface NormalizerStore extends AutoCloseableRuntime {

  GraphDatabaseService getNeo();

  /**
   * get taxon by its unique taxonID
   */
  Node byTaxonID(String taxonID);

  List<Node> byScientificName(String scientificName);

  List<Node> byScientificName(String scientificName, Rank rank);

  Dataset getDataset();

  void startBatchMode();

  void endBatchMode();

  NeoTaxon put(NeoTaxon taxon);

  Reference put(Reference r);

  Dataset put(Dataset d);

  /**
   * Process all nodes in batches with the given callback handler.
   * Every batch is processed in a single transaction which is committed at the end of the batch.
   *
   * If new nodes are created within a batch transaction this will be also be returned to the callback handler at the very end.
   *
   * Iteration is by node value starting from node value 1 to highest.
   *
   * @param batchSize
   * @param callback
   */
  void processAll(int batchSize, NeoDb.NodeBatchProcessor callback);

  NeoTaxon get(Node n);

  void updateTaxonStoreWithRelations();

  /**
   * Set correct ROOT and PROPARTE labels for easier access
   */
  void updateLabels();
}
