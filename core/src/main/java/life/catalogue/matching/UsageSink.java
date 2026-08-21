package life.catalogue.matching;

import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.matching.nidx.NameIndex;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Anything usages can be loaded into: a mutable {@link UsageMatcherStore} or the write only
 * {@link UsageMatcherFileStoreBuilder} that produces a sealed one.
 */
public interface UsageSink {
  Logger LOG = LoggerFactory.getLogger(UsageSink.class);

  int datasetKey();

  /**
   * Adds a usage. If the same id was added before this behaves like an update.
   */
  void add(SimpleNameCached sn);

  /**
   * Loads the dataset, using the previously persisted name matches.
   */
  default int load(SqlSessionFactory factory) {
    LOG.info("Start loading all usages from dataset {}", datasetKey());
    var cnt = new AtomicInteger();
    try (SqlSession session = factory.openSession()) {
      var num = session.getMapper(NameUsageMapper.class);
      PgUtils.consume(() -> num.processDatasetSimpleNidx(datasetKey()), sn -> {
        add(sn);
        cnt.incrementAndGet();
      });
    }
    LOG.info("Loaded {} usages for dataset {}", cnt, datasetKey());
    return cnt.intValue();
  }

  /**
   * Loads the dataset, using an explicit names index to (re)match all usages against before they are
   * added. Writes to the names index are allowed.
   */
  default int load(SqlSessionFactory factory, NameIndex ni) {
    LOG.info("Start loading all usages from dataset {}", datasetKey());
    var cnt = new AtomicInteger();
    try (SqlSession session = factory.openSession()) {
      var num = session.getMapper(NameUsageMapper.class);
      PgUtils.consume(() -> num.processDataset(datasetKey()), u -> {
        matchAndAdd(u, ni);
        cnt.incrementAndGet();
      });
    }
    LOG.info("Loaded {} usages for dataset {}", cnt, datasetKey());
    return cnt.intValue();
  }

  private void matchAndAdd(NameUsageBase u, NameIndex ni) {
    var m = ni.match(u.getName(), true, false);
    u.getName().applyMatch(m);
    add(new SimpleNameCached(u, m.getNidx()));
  }
}
