package life.catalogue.release;

import life.catalogue.api.model.DSID;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.common.id.IdConverter;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.db.mapper.*;
import life.catalogue.junit.NameMatchingRule;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ibatis.session.SqlSession;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class IdProviderArchiveIT {

  public final static TestDataRule.TestData TEST_DATA = new TestDataRule.TestData("idprovider", 3,
    Map.of(
    "name_usage_archive", Map.of(
      "dataset_key", 3,
        "n_id", "xyz"
      )
    ), Set.of(3), true);
  final int projectKey = TEST_DATA.key;

  @ClassRule
  public static SqlSessionFactoryRule pgSetupRule = new PgSetupRule();

  IdProvider provider;
  NameMatchingRule matchingRule = new NameMatchingRule();

  @Rule
  public final TestRule chain = RuleChain
    .outerRule(new TestDataRule(TEST_DATA))
    .around(matchingRule);
  private ReleaseConfig cfg;


  public void init(ReleaseConfig cfg, ProjectReleaseConfig prCfg) throws IOException {
    this.cfg = cfg;
    provider = new IdProvider(projectKey, projectKey, DatasetOrigin.RELEASE, 1, -1, cfg, prCfg, SqlSessionFactoryRule.getSqlSessionFactory());
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
  public void mapIds() throws Exception {
    init(new ReleaseConfig(), new ProjectReleaseConfig());
    // verify archived names got loaded
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      assertNotNull( session.getMapper(ArchivedNameUsageMapper.class).get(DSID.of(projectKey, "56TT9")));
      assertNotNull( session.getMapper(ArchivedNameUsageMapper.class).get(DSID.of(projectKey, "LR53R")));
      assertNotNull( session.getMapper(ArchivedNameUsageMapper.class).get(DSID.of(projectKey, "R57MB")));
    }

    provider.mapAllIds();
    //provider.report();
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      IdMapMapper idm = session.getMapper(IdMapMapper.class);
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      NameMatchMapper nmm = session.getMapper(NameMatchMapper.class);
      // report
      AtomicInteger maxID = new AtomicInteger();
      num.processDataset(projectKey, null, null).forEach(nu -> {
        System.out.print(nu);
        var nm = nmm.get(nu.getName());
        var ni = nm.getName();
        var id = idm.getUsage(projectKey, nu.getId());
        System.out.println("  -> " + id + " nidx:" + ni);
        int val = IdConverter.LATIN29.decode(id);
        maxID.set(Math.max(val, maxID.get()));
      });
      // no id issued
      assertTrue(provider.getReport().created.isEmpty());
      assertTrue(provider.getReport().deleted.isEmpty());

      // assert
      assertEquals(8, idm.countUsage(projectKey));
      assertEquals("56TT9", idm.getUsage(projectKey, "t1"));
      assertEquals("5HP96", idm.getUsage(projectKey, "t2"));
      assertEquals("5KH5P", idm.getUsage(projectKey, "t3"));
      assertEquals("627WF", idm.getUsage(projectKey, "t4"));
      assertEquals("679P", idm.getUsage(projectKey, "t5"));
      assertEquals("679Q", idm.getUsage(projectKey, "t6"));
      assertEquals("6QPSN", idm.getUsage(projectKey, "t7"));
      assertEquals("8K9Y", idm.getUsage(projectKey, "t8"));
    }
  }
}