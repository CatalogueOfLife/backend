package life.catalogue.matching;

public class UsageMatcherMemStoreTest extends UsageMatcherStoreTestBase {

  @Override
  UsageMatcherStore createStore(int datasetKey) {
    return new UsageMatcherMemStore(datasetKey);
  }

}