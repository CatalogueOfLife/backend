package org.col.commands.importer.neo;

import org.col.api.Dataset;
import org.col.api.Reference;
import org.col.commands.importer.neo.model.NeoTaxon;
import org.col.common.AutoCloseableRuntime;
import org.neo4j.graphdb.GraphDatabaseService;

import java.util.stream.Stream;

/**
 *
 */
public interface NormalizerStore extends AutoCloseableRuntime {

  Dataset getDataset();

  void startBatchMode();

  void endBatchMode();

  void put(NeoTaxon taxon);

  void put(Reference r);

  void put(Dataset d);

  GraphDatabaseService getNeo();

  Stream<NeoTaxon> all();

  Stream<NeoTaxon> originalNames();

}
