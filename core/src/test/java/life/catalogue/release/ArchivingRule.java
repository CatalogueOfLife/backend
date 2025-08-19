package life.catalogue.release;

import life.catalogue.dao.NameUsageArchiver;

import life.catalogue.junit.SqlSessionFactoryRule;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A junit test rule that archives all releases
 */
public class ArchivingRule extends ExternalResource {
  private static final Logger LOG = LoggerFactory.getLogger(ArchivingRule.class);
  private final SqlSessionFactory factory;
  private final NameUsageArchiver archiver;

  public ArchivingRule() {
    this.factory = SqlSessionFactoryRule.getSqlSessionFactory();
    this.archiver = new NameUsageArchiver(factory);
  }

  @Override
  public void before() throws Throwable {
    LOG.info("Rebuild name release archive");
    archiver.rebuildAll(true);
  }

  public NameUsageArchiver getArchiver() {
    return archiver;
  }
}
