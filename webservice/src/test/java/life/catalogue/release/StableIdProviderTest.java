package life.catalogue.release;

import life.catalogue.api.vocab.Gazetteer;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.db.mapper.IdMapMapper;
import org.apache.ibatis.session.SqlSession;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class StableIdProviderTest {

  public final static TestDataRule.TestData PROJECT_DATA = new TestDataRule.TestData("project", 3, 2, 2, Map.of(
    "distribution_3", Map.of("gazetteer", Gazetteer.ISO, "reference_id", "Flade2008")
  ),3,11,12,13);
  final int projectKey = PROJECT_DATA.key;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  StableIdProvider provider;
  NameMatchingRule matchingRule = new NameMatchingRule();

  @Rule
  public final TestRule chain = RuleChain
    .outerRule(new TestDataRule(PROJECT_DATA))
    .around(matchingRule);


  @Before
  public void init() {
    provider = StableIdProvider.withAllReleases(projectKey,3, PgSetupRule.getSqlSessionFactory());
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
      assertEquals(25, idm.countUsage(projectKey));
      assertEquals("R", idm.getUsage(projectKey, "25"));
      // rufus -> rufa
      assertEquals("E", idm.getUsage(projectKey, "14"));
      // baileyi -> baileii
      assertEquals("3H", idm.getUsage(projectKey, "15"));
    }
  }

}