package life.catalogue.release;

import life.catalogue.api.model.DSID;
import life.catalogue.api.vocab.Gazetteer;
import life.catalogue.common.id.IdConverter;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.*;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ibatis.session.SqlSession;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class IdProviderIT {

  public final static TestDataRule.TestData PROJECT_DATA = new TestDataRule.TestData("project", 3, 1, 2,
    Map.of(
    "distribution", Map.of("gazetteer", Gazetteer.ISO, "reference_id", "Flade2008"),
      "sector", Map.of("created_by", 100, "modified_by", 100)
    ), Set.of(3,11,12,13));
  final int projectKey = PROJECT_DATA.key;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  IdProvider provider;
  NameMatchingRule matchingRule = new NameMatchingRule();

  @Rule
  public final TestRule chain = RuleChain
    .outerRule(new TestDataRule(PROJECT_DATA))
    .around(matchingRule);
  private ReleaseConfig cfg;


  @Before
  public void init() throws IOException {
    cfg = new ReleaseConfig();
    provider = new IdProvider(projectKey, 1, -1, cfg, SqlSessionFactoryRule.getSqlSessionFactory());
    System.out.println("Create id mapping tables for project " + projectKey);
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      DatasetPartitionMapper dmp = session.getMapper(DatasetPartitionMapper.class);
      DatasetPartitionMapper.IDMAP_TABLES.forEach(t -> dmp.createIdMapTable(t, projectKey));
    }
  }

  @After
  public void destroy() {
    System.out.println("Remove id mapping tables for project " + projectKey);
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      DatasetPartitionMapper dmp = session.getMapper(DatasetPartitionMapper.class);
      DatasetPartitionMapper.IDMAP_TABLES.forEach(t -> dmp.dropTable(t, projectKey));
    }
    org.apache.commons.io.FileUtils.deleteQuietly(cfg.reportDir);
  }

  @Test
  public void run() throws Exception {
    // verify archived names got loaded
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      assertNotNull( session.getMapper(ArchivedNameUsageMapper.class).get(DSID.of(projectKey, "M")));
    }

    provider.run();
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      IdMapMapper idm = session.getMapper(IdMapMapper.class);
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      NameMatchMapper nmm = session.getMapper(NameMatchMapper.class);
      // report
      AtomicInteger maxID = new AtomicInteger();
      num.processDataset(projectKey, null, null).forEach(nu -> {
        System.out.print(nu);
        var ni = nmm.get(nu).getName();
        var id = idm.getUsage(projectKey, nu.getId());
        System.out.println("  -> " + id + " nidx:" + ni);
        int val = IdConverter.LATIN29.decode(id);
        maxID.set(Math.max(val, maxID.get()));
      });
      // largest id issued is:
      assertEquals("B5", IdConverter.LATIN29.encode(maxID.get()));

      // assert
      assertEquals(25, idm.countUsage(projectKey));
      assertEquals("R", idm.getUsage(projectKey, "25"));
      // rufus -> rufa
      // current 14 is synonym Felis rufa with parent Lynx rufus (13):
      // release 13 contains Felis rufus as synonym B2 with parent Lynx rufus (D)
      assertEquals("B2", idm.getUsage(projectKey, "14"));
      // baileyi -> baileii
      assertEquals("F", idm.getUsage(projectKey, "15"));
    }
  }

}