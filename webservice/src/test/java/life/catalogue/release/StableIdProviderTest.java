package life.catalogue.release;

import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.db.mapper.IdMapMapper;
import org.apache.ibatis.session.SqlSession;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;

public class StableIdProviderTest {
  final int projectKey = TestDataRule.TestData.PROJECT.key;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  StableIdProvider provider;
  NameMatchingRule matchingRule = new NameMatchingRule();

  @Rule
  public final TestRule chain = RuleChain
    .outerRule(TestDataRule.project())
    .around(matchingRule);


  @Before
  public void init() {
    provider = new StableIdProvider(projectKey,3, PgSetupRule.getSqlSessionFactory());
    System.out.println("Create id mapping tables for project " + projectKey);
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      DatasetPartitionMapper dmp = session.getMapper(DatasetPartitionMapper.class);
      DatasetPartitionMapper.IDMAP_TABLES.forEach(t -> dmp.createIdMapTable(t, projectKey));
    }
  }

  @After
  public void destroy() {
    System.out.println("Remove id mapping tables for project " + projectKey);
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      DatasetPartitionMapper dmp = session.getMapper(DatasetPartitionMapper.class);
      DatasetPartitionMapper.IDMAP_TABLES.forEach(t -> dmp.deleteTable(t, projectKey));
    }
  }

  @Test
  public void run() throws Exception {
    provider.run();
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      IdMapMapper idm = session.getMapper(IdMapMapper.class);
      assertEquals(24, idm.countUsage(projectKey));
      assertEquals("R", idm.getUsage(projectKey, "25"));
    }
  }

}