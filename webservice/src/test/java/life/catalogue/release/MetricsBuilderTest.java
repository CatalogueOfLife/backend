package life.catalogue.release;

import life.catalogue.api.model.DSID;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.db.mapper.TaxonMetricsMapper;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

public class MetricsBuilderTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.tree();

  @Test
  public void rebuildMetrics() {
    MetricsBuilder.rebuildMetrics(SqlSessionFactoryRule.getSqlSessionFactory(), testDataRule.testData.key);
    var key = DSID.<String>root(testDataRule.testData.key);

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      var tmm = session.getMapper(TaxonMetricsMapper.class);
      var tm = session.getMapper(TaxonMapper.class);
      var tcnt = tm.count(key.getDatasetKey());
      assertEquals(20, tcnt);
      assertEquals(tcnt, tmm.count(key.getDatasetKey()));
      // t1 Animalia
      var m = tmm.get(key.id("t1"));
      assertTrue(m.getSourceDatasetKeys().isEmpty());
      assertEquals(1, m.getChildCount());
      assertEquals(1, m.getChildExtantCount());
      assertEquals(19, m.getTaxonCount());
      assertEquals(7, m.getMaxDepth());
      assertEquals(0, m.getDepth());

      // t6 family Felidae
      m = tmm.get(key.id("t6"));
      assertTrue(m.getSourceDatasetKeys().isEmpty());
      assertEquals(1, m.getChildCount());
      assertEquals(1, m.getChildExtantCount());
      assertEquals(5, m.getTaxonCount());
      assertEquals(7, m.getMaxDepth());
      assertEquals(4, m.getDepth());

      // t30 genus Urocyon
      m = tmm.get(key.id("t30"));
      assertTrue(m.getSourceDatasetKeys().isEmpty());
      assertEquals(4, m.getChildCount());
      assertEquals(4, m.getChildExtantCount());
      assertEquals(4, m.getTaxonCount());
      assertEquals(5, m.getMaxDepth());
      assertEquals(4, m.getDepth());
    }
  }
}