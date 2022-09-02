package life.catalogue.db;

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

  public NameMatchingRule() {
    this(PgSetupRule::getSqlSessionFactory);
  }

  public NameMatchingRule(Supplier<SqlSessionFactory> sqlSessionFactorySupplier) {
    this.factorySupplier = sqlSessionFactorySupplier;
  }

  @Override
  protected void before() throws Throwable {
    SqlSessionFactory factory = factorySupplier.get();
    LOG.info("Setup names index");
    nidx = NameIndexFactory.memory(factory, AuthorshipNormalizer.INSTANCE);
    nidx.start();
    LOG.info("Rematch all names");
    RematchJob.all(TestDataRule.TEST_USER.getKey(), factory, nidx).run();
  }

  public void rematch(int datasetKey) {
    RematchJob.one(TestDataRule.TEST_USER.getKey(), factorySupplier.get(), nidx, datasetKey).run();
  }

  public static NameIndex getIndex() {
    return nidx;
  }

  @Override
  protected void after() {
    try {
      nidx.close();
    } catch (Exception e) {
      LOG.error("Failed to close NameMatch rule", e);
    }
  }
}
