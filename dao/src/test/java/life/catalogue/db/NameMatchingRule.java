package life.catalogue.db;

import life.catalogue.api.vocab.Users;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.NameIndex;
import life.catalogue.matching.NameIndexFactory;
import life.catalogue.matching.RematchJob;

import java.util.function.Supplier;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A junit test rule that sets up a new in memory names index and rematches all names
 */
public class NameMatchingRule extends ExternalResource {
  private static final Logger LOG = LoggerFactory.getLogger(NameMatchingRule.class);

  private static NameIndex nidx;
  private final Supplier<SqlSessionFactory> factorySupplier;
  private final boolean matchAll;
  private final boolean reuseNidx;

  public NameMatchingRule() {
    this(SqlSessionFactoryRule::getSqlSessionFactory, true, false);
  }

  public NameMatchingRule(boolean reuseNidx) {
    this(SqlSessionFactoryRule::getSqlSessionFactory, true, reuseNidx);
  }

  public NameMatchingRule(Supplier<SqlSessionFactory> sqlSessionFactorySupplier, boolean matchAll, boolean reuseNidx) {
    this.factorySupplier = sqlSessionFactorySupplier;
    this.matchAll = matchAll;
    this.reuseNidx = reuseNidx;
  }

  @Override
  public void before() throws Throwable {
    SqlSessionFactory factory = factorySupplier.get();
    LOG.info("Setup in-memory names index");
    if (nidx == null || !reuseNidx) {
      nidx = NameIndexFactory.memory(factory, AuthorshipNormalizer.INSTANCE);
      nidx.start();
    }
    if (matchAll) {
      LOG.info("Rematch all names");
      rematchAll();
    }
  }

  public void rematchAll() {
    RematchJob.all(Users.MATCHER, factorySupplier.get(), nidx).run();
  }

  public void rematch(int datasetKey) {
    RematchJob.one(Users.MATCHER, factorySupplier.get(), nidx, datasetKey).run();
  }

  public static NameIndex getIndex() {
    return nidx;
  }

  @Override
  public void after() {
    if (!reuseNidx) {
      try {
        nidx.close();
      } catch (Exception e) {
        LOG.error("Failed to close NameMatch rule", e);
      }
    }
  }
}
