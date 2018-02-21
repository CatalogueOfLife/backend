package org.col.admin.task.importer.neo;

import org.col.admin.task.importer.neo.model.Labels;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.api.model.Dataset;
import org.col.lang.AutoCloseableRuntime;
import org.gbif.nameparser.api.Rank;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;

import java.util.List;
import java.util.Optional;

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

  Optional<Dataset> getDataset();

  void startBatchMode();

  void endBatchMode();

  NeoTaxon put(NeoTaxon taxon);

  Dataset put(Dataset d);

  /**
   * Process all nodes in batches with the given callback handler.
   * Every batch is processed in a single transaction which is committed at the end of the batch.
   *
   * If new nodes are created within a batch transaction this will be also be returned to the callback handler at the very end.
   *
   * Iteration is by node value starting from node value 1 to highest.
   *
   * @param label neo4j node label to select nodes by. Use Labels.ALL for all nodes
   * @param batchSize
   * @param callback
   */
  void process(Labels label, int batchSize, NeoDb.NodeBatchProcessor callback);

  Iterable<NeoTaxon> all();

  NeoTaxon get(Node n);

  void updateTaxonStoreWithRelations();

  /**
   * Set correct ROOT, PROPARTE and BASIONYM labels for easier access
   */
  void updateLabels();
}
