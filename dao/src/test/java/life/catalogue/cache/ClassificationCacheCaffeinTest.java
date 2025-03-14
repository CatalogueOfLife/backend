package life.catalogue.cache;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.junit.PgSetupRule;

import life.catalogue.junit.SqlSessionFactoryRule;

import life.catalogue.junit.TestDataRule;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import org.apache.ibatis.session.SqlSession;
import org.junit.*;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClassificationCacheCaffeinTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.tree();

  SqlSession session;
  ClassificationCache cache;

  @Before
  public void init() throws Exception {
    session = PgSetupRule.getSqlSessionFactory().openSession();
    cache = new ClassificationCacheCaffein(testDataRule.testData.key, session, 100);
  }

  @After
  public void destroy() throws Exception {
    session.close();
    cache.close();
  }

  @Test
  public void crud() throws Exception {
    final SimpleNameCached sn = new SimpleNameCached();
    sn.setId("a");
    sn.setName("Abies");
    sn.setRank(Rank.GENUS);
    sn.setStatus(TaxonomicStatus.MISAPPLIED);
    sn.setCode(NomCode.BOTANICAL);
    sn.setNamesIndexMatchType(MatchType.EXACT);

    assertFalse(cache.contains("a"));

    cache.put(sn);
    assertTrue(cache.contains("a"));
    assertEquals(sn, cache.get("a"));

    assertTrue(cache.contains(sn.getId()));
  }

  @Test
  public void withClassification() {
    var key = "s11";

    assertFalse(cache.contains(key));
    var snp = cache.get(key);
    assertNotNull(snp);

    var sncl = cache.withClassification(snp);
    assertEquals(6, sncl.getClassification().size());
  }
}