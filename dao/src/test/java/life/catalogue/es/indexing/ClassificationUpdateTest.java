package life.catalogue.es.indexing;

import life.catalogue.api.model.DSID;
import life.catalogue.common.io.TempFile;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.es.EsSetupRule;
import life.catalogue.es.EsUtil;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.TestDataRule;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

/**
 * The draft_with_sectors tree used here contains:
 *
 * <pre>
 *   k1 - p1
 *      - p2 - c1
 *           - c2
 *      - p3 - c3
 *   k5 - p6
 *      - p7
 * </pre>
 *
 * Moving p2 from k1 to k5 must rewrite the classification of p2 itself and of both its children,
 * while leaving the p3 branch untouched.
 */
public class ClassificationUpdateTest {

  static PgSetupRule pgSetupRule = new PgSetupRule();
  static TestDataRule testDataRule = TestDataRule.draftWithSectors();
  static EsSetupRule esSetup = new EsSetupRule();

  @ClassRule
  public final static TestRule classRules = RuleChain
    .outerRule(pgSetupRule)
    .around(testDataRule)
    .around(esSetup);

  NameUsageIndexServiceEs service;
  TempFile dir;
  final int datasetKey = 3;

  @Before
  public void init() throws IOException {
    dir = TempFile.directory();
    service = new NameUsageIndexServiceEs(esSetup.getClient(), esSetup.getEsConfig(), dir.file, PgSetupRule.getSqlSessionFactory());
    // the test data rule only resets per class, so undo any move a previous test made before indexing
    movePg("p2", "k1");
    service.createEmptyIndex();
    service.indexDataset(datasetKey);
  }

  @After
  public void tearDown() throws IOException {
    dir.close();
  }

  @Test
  public void movedTaxonRewritesDescendantClassifications() throws Exception {
    assertEquals(List.of("k1", "p2"), classificationIds("p2"));
    assertEquals(List.of("k1", "p2", "c1"), classificationIds("c1"));
    assertEquals(List.of("k1", "p2", "c2"), classificationIds("c2"));
    assertEquals(List.of("k1", "p3", "c3"), classificationIds("c3"));

    movePg("p2", "k5");
    awaitTask(service.updateClassification(datasetKey, "p2"));
    EsUtil.refreshIndex(esSetup.getClient(), esSetup.getEsConfig().index.name);

    // the moved taxon and everything below it now hangs under k5
    assertEquals(List.of("k5", "p2"), classificationIds("p2"));
    assertEquals(List.of("k5", "p2", "c1"), classificationIds("c1"));
    assertEquals(List.of("k5", "p2", "c2"), classificationIds("c2"));
    // a sibling branch under the old parent is untouched
    assertEquals(List.of("k1", "p3", "c3"), classificationIds("c3"));
  }

  /**
   * An update rebuilds each document from its _source. Anything the mapping indexes but _source does
   * not carry would silently disappear here, so guard the fields that are only ever written.
   */
  @Test
  public void updateKeepsIndexedFields() throws Exception {
    movePg("p2", "k5");
    awaitTask(service.updateClassification(datasetKey, "p2"));
    EsUtil.refreshIndex(esSetup.getClient(), esSetup.getEsConfig().index.name);

    assertTrue("usage.label must survive the update", hits("usage.label", label("c1")) == 1);
    assertTrue("usage.name.alphaIndex must survive the update", hits("usage.name.alphaIndex", alphaIndex("c1")) > 0);
  }

  @Test
  public void unknownRootIsANoop() throws Exception {
    awaitTask(service.updateClassification(datasetKey, "does-not-exist"));
    assertEquals(List.of("k1", "p2", "c1"), classificationIds("c1"));
  }

  private void movePg(String id, String newParentId) {
    try (SqlSession s = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      s.getMapper(NameUsageMapper.class).updateParentId(DSID.of(datasetKey, id), newParentId, TestDataRule.TEST_USER.getKey());
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> source(String usageId) throws IOException {
    var resp = esSetup.getClient().search(s -> s
      .index(esSetup.getEsConfig().index.name)
      .query(q -> q.term(t -> t.field("id").value(usageId))), Map.class);
    assertEquals("expected exactly one doc for " + usageId, 1, resp.hits().hits().size());
    return (Map<String, Object>) resp.hits().hits().get(0).source();
  }

  @SuppressWarnings("unchecked")
  private List<String> classificationIds(String usageId) throws IOException {
    var cl = (List<Map<String, Object>>) source(usageId).get("classification");
    return cl.stream().map(m -> (String) m.get("id")).toList();
  }

  @SuppressWarnings("unchecked")
  private String label(String usageId) throws IOException {
    return (String) ((Map<String, Object>) source(usageId).get("usage")).get("label");
  }

  @SuppressWarnings("unchecked")
  private String alphaIndex(String usageId) throws IOException {
    var usage = (Map<String, Object>) source(usageId).get("usage");
    return (String) ((Map<String, Object>) usage.get("name")).get("alphaIndex");
  }

  private long hits(String field, String value) throws IOException {
    var resp = esSetup.getClient().search(s -> s
      .index(esSetup.getEsConfig().index.name)
      .size(0)
      .query(q -> q.term(t -> t.field(field).value(value))), Void.class);
    return resp.hits().total().value();
  }

  private void awaitTask(String taskId) throws Exception {
    if (taskId == null) return;
    for (int i = 0; i < 100; i++) {
      if (esSetup.getClient().tasks().get(g -> g.taskId(taskId)).completed()) return;
      Thread.sleep(100);
    }
    fail("ES task " + taskId + " did not complete");
  }
}
