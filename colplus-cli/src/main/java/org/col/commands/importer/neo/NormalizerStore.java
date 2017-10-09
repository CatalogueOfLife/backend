package org.col.commands.importer.neo;

import org.col.api.Reference;
import org.col.commands.importer.neo.model.NeoTaxon;
import org.col.common.AutoCloseableRuntime;

/**
 *
 */
public interface NormalizerStore extends AutoCloseableRuntime {

  int getDatasetKey();

  void startBatchMode();

  void endBatchMode();

  void put(NeoTaxon taxon);

  void put(Reference r);
}
