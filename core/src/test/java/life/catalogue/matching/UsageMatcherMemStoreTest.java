package life.catalogue.matching;

public class UsageMatcherMemStoreTest extends UsageMatcherStoreTestBase {

  @Override
  UsageSink createSink(int datasetKey) {
    return new UsageMatcherMemStore(datasetKey);
  }

  @Override
  UsageMatcherStore seal(UsageSink sink) {
    return (UsageMatcherStore) sink;
  }
}
