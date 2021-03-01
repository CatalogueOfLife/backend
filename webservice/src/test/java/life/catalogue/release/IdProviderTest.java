package life.catalogue.release;

import life.catalogue.api.model.SimpleNameWithNidx;
import life.catalogue.api.vocab.Gazetteer;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.db.mapper.IdMapMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.checkerframework.checker.units.qual.A;
import org.gbif.utils.file.FileUtils;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IdProviderTest {


  @ClassRule
  public final static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule dataRule = TestDataRule.draft();
  final int projectKey = dataRule.testData.key;

  ReleaseConfig cfg;
  Map<Integer, List<SimpleNameWithNidx>> prevIdsByAttempt = new HashMap<>();


  @Before
  public void init() throws IOException {
    cfg = new ReleaseConfig();
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
    org.apache.commons.io.FileUtils.deleteQuietly(cfg.reportDir);
  }

  class IdTestProvider extends IdProvider {

    public IdTestProvider() {
      super(projectKey, prevIdsByAttempt.isEmpty() ? 1 : Collections.max(prevIdsByAttempt.keySet())+1, cfg, PgSetupRule.getSqlSessionFactory());
    }

    @Override
    protected void report() {
      // dont report
    }

    @Override
    protected void loadPreviousReleaseIds() {
      // dont do anything. load release ids manually
      for (Map.Entry<Integer, List<SimpleNameWithNidx>> rel : prevIdsByAttempt.entrySet()) {
        int attempt = rel.getKey();
        int datasetKey = 1000 + attempt;
        for (SimpleNameWithNidx sn : rel.getValue()) {
          addReleaseId(datasetKey,attempt, sn);
        }
      }
    }
  }

  @Test
  public void nothing() throws Exception {

    IdProvider provider = new IdTestProvider();
    provider.mapCanonicalGroup(new ArrayList<>(List.of()));

    IdProvider.IdReport report = provider.getReport();
    assertTrue(report.created.isEmpty());
    assertTrue(report.deleted.isEmpty());
    assertTrue(report.resurrected.isEmpty());

  }

  @Test
  public void basic() throws Exception {
    IdProvider provider = new IdTestProvider();
    // 1st attempt
    prevIdsByAttempt.put(1, List.of());
    // 2nd attempt
    prevIdsByAttempt.put(2, List.of());

    provider.mapCanonicalGroup(new ArrayList<>(List.of()));

    IdProvider.IdReport report = provider.getReport();
    assertTrue(report.created.isEmpty());
    assertTrue(report.deleted.isEmpty());
    assertTrue(report.resurrected.isEmpty());

  }

}