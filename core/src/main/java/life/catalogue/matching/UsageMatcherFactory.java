package life.catalogue.matching;

import life.catalogue.matching.nidx.NameIndex;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

/**
 * Factory to create and reuse persistent usage matchers,
 * which are specific for an entire dataset.
 */
public class UsageMatcherFactory {
  private final static Logger LOG = LoggerFactory.getLogger(UsageMatcherFactory.class);
  private final NameIndex nameIndex;
  private final SqlSessionFactory factory;

  public UsageMatcherFactory(NameIndex nameIndex, SqlSessionFactory factory) {
    this.nameIndex = Preconditions.checkNotNull(nameIndex);
    this.factory = Preconditions.checkNotNull(factory);
  }

  public NameIndex getNameIndex() {
    return nameIndex;
  }

  public UsageMatcher persistent(int datasetKey) {
    LOG.info("Create new persistent matcher for dataset {}", datasetKey);
    //TODO: use reusable file persistent storage !!!
    return memory(datasetKey);
  }

  public UsageMatcher memory(int datasetKey) {
    LOG.info("Create new in memory matcher for dataset {}", datasetKey);
    var store = new UsageMatcherMemStore();
    store.load(datasetKey, factory);
    return new UsageMatcher(datasetKey, nameIndex, store);
  }
}
