package life.catalogue.es.indexing;

import life.catalogue.api.model.SimpleNameClassification;

import java.util.List;
import java.util.function.Consumer;

public class ClassificationUpdater implements Consumer<List<SimpleNameClassification>> {
  public ClassificationUpdater(NameUsageIndexer indexer, int datasetKey) {

  }

  @Override
  public void accept(List<SimpleNameClassification> ts) {

  }
}
