package life.catalogue.es.nu.search;

import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import life.catalogue.api.model.EditorialDecision.Mode;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.search.SimpleDecision;
import life.catalogue.es.EsReadTestBase;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DecisionQueriesTest extends EsReadTestBase {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchServiceTest.class);

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void test1() {

    SimpleDecision sd1 = new SimpleDecision();
    sd1.setId(100);
    sd1.setDatasetKey(1);

    SimpleDecision sd2a = new SimpleDecision();
    sd2a.setId(101);
    sd2a.setDatasetKey(2);

    SimpleDecision sd2b = new SimpleDecision();
    sd2b.setId(102);
    sd2b.setDatasetKey(2);

    SimpleDecision sd2c = new SimpleDecision();
    sd2c.setId(103);
    sd2c.setDatasetKey(2);

    SimpleDecision sd3 = new SimpleDecision();
    sd3.setId(104);
    sd3.setDatasetKey(3);

    List<NameUsageWrapper> nuws = createNameUsages(2);
    nuws.get(0).setDecisions(null);
    nuws.get(1).setDecisions(Arrays.asList(sd1, sd2a, sd2b));
    index(nuws);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, 77);
    List<NameUsageWrapper> result = search(query).getResult();

    /*
     * No decision mode present as query param so catalog key should NOT function as document filter, but only to prune decisions with a
     * decision key other than 77. So for the second nuw, that means ALL decisions are pruned away.
     */
    assertEquals(2, result.size());
    assertNull(result.get(0).getDecisions());
    assertNull(result.get(1).getDecisions()); // all decisions pruned away
  }

  @Test
  public void test2() {
    SimpleDecision sd1 = new SimpleDecision();
    sd1.setDatasetKey(1);
    SimpleDecision sd2a = new SimpleDecision();
    sd2a.setDatasetKey(2);
    sd2a.setMode(Mode.BLOCK);
    SimpleDecision sd2b = new SimpleDecision();
    sd2b.setDatasetKey(2);
    sd2b.setMode(Mode.REVIEWED);
    SimpleDecision sd2c = new SimpleDecision();
    sd2c.setDatasetKey(2);
    sd2c.setMode(Mode.UPDATE);
    SimpleDecision sd3 = new SimpleDecision();
    sd3.setDatasetKey(3);

    List<NameUsageWrapper> nuws = createNameUsages(2);
    nuws.get(0).setDecisions(Arrays.asList(sd1));
    nuws.get(1).setDecisions(Arrays.asList(sd2a, sd2b, sd2c));
    index(nuws);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, 2);
    List<NameUsageWrapper> result = search(query).getResult();
    // Make sure catalog key does NOT function as a document filter:
    assertEquals(2, result.size());
    assertNull(result.get(0).getDecisions());
    assertEquals(1, result.get(1).getDecisions().size());
    assertEquals(Mode.BLOCK, result.get(1).getDecisions().get(0).getMode());
  }

  @Test
  public void test3() {
    SimpleDecision sd1 = new SimpleDecision();
    sd1.setId(100);
    sd1.setDatasetKey(1);
    sd1.setMode(Mode.UPDATE_RECURSIVE);

    SimpleDecision sd2a = new SimpleDecision();
    sd2a.setId(101);
    sd2a.setDatasetKey(2);
    sd2a.setMode(Mode.BLOCK);

    SimpleDecision sd2b = new SimpleDecision();
    sd2b.setId(102);
    sd2b.setDatasetKey(2);
    sd2b.setMode(Mode.REVIEWED);

    SimpleDecision sd2c = new SimpleDecision();
    sd2c.setId(103);
    sd2c.setDatasetKey(2);
    sd2c.setMode(Mode.UPDATE);

    SimpleDecision sd3 = new SimpleDecision();
    sd3.setId(104);
    sd3.setDatasetKey(3);
    sd3.setMode(Mode.UPDATE);

    List<NameUsageWrapper> nuws = createNameUsages(3);
    nuws.get(0).setDecisions(Arrays.asList(sd1)); // no match
    nuws.get(1).setDecisions(Arrays.asList(sd2a, sd2b, sd2c)); // match
    nuws.get(2).setDecisions(Arrays.asList(sd3)); // match
    index(nuws);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(NameUsageSearchParameter.DECISION_MODE, Mode.UPDATE);
    query.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, 2);
    query.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, 3);
    List<NameUsageWrapper> result = search(query).getResult();

    assertEquals(2, result.size());
    assertEquals(1, result.get(0).getDecisions().size()); // Pruned to 1 decision
    assertEquals(1, result.get(1).getDecisions().size());
  }

  @Test
  public void test4() {

    SimpleDecision sd1 = new SimpleDecision();
    sd1.setId(100);
    sd1.setDatasetKey(1);
    sd1.setMode(Mode.UPDATE_RECURSIVE);

    SimpleDecision sd2a = new SimpleDecision();
    sd2a.setId(101);
    sd2a.setDatasetKey(2);
    sd2a.setMode(Mode.BLOCK);

    SimpleDecision sd2b = new SimpleDecision();
    sd2b.setId(102);
    sd2b.setDatasetKey(2);
    sd2b.setMode(Mode.REVIEWED);

    SimpleDecision sd2c = new SimpleDecision();
    sd2c.setId(103);
    sd2c.setDatasetKey(2);
    sd2c.setMode(Mode.UPDATE);

    SimpleDecision sd3 = new SimpleDecision();
    sd3.setId(104);
    sd3.setDatasetKey(3);
    sd3.setMode(Mode.UPDATE);

    List<NameUsageWrapper> nuws = createNameUsages(3);
    nuws.get(0).setDecisions(Arrays.asList(sd1));
    nuws.get(1).setDecisions(Arrays.asList(sd2a, sd2b, sd2c)); // match
    nuws.get(2).setDecisions(Arrays.asList(sd3)); // match
    index(nuws);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(NameUsageSearchParameter.DECISION_MODE, Mode.UPDATE);
    query.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, 2);
    query.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, 3);
    List<NameUsageWrapper> result = search(query).getResult();

    assertEquals(2, result.size());
    assertEquals(1, result.get(0).getDecisions().size()); // Pruned to 1 decision
    assertEquals(1, result.get(1).getDecisions().size());
  }

  @Test
  public void test5() {

    SimpleDecision sd1 = new SimpleDecision();
    sd1.setId(100);
    sd1.setDatasetKey(1);
    sd1.setMode(Mode.UPDATE);

    SimpleDecision sd2a = new SimpleDecision();
    sd2a.setId(101);
    sd2a.setDatasetKey(2);
    sd2a.setMode(Mode.BLOCK);

    SimpleDecision sd2b = new SimpleDecision();
    sd2b.setId(102);
    sd2b.setDatasetKey(2);
    sd2b.setMode(Mode.REVIEWED);

    SimpleDecision sd2c = new SimpleDecision();
    sd2c.setId(103);
    sd2c.setDatasetKey(2);
    sd2c.setMode(Mode.UPDATE);

    SimpleDecision sd3 = new SimpleDecision();
    sd3.setId(104);
    sd3.setDatasetKey(3);
    sd3.setMode(Mode.UPDATE);

    List<NameUsageWrapper> nuws = createNameUsages(3);
    nuws.get(0).setDecisions(Arrays.asList(sd1));
    nuws.get(1).setDecisions(Arrays.asList(sd2a, sd2b, sd2c)); // match
    nuws.get(2).setDecisions(Arrays.asList(sd3)); // match
    index(nuws);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(NameUsageSearchParameter.DECISION_MODE, Mode.UPDATE);
    List<NameUsageWrapper> result = search(query).getResult();

    assertEquals(3, result.size());
    assertEquals(1, result.get(0).getDecisions().size());
    assertEquals(3, result.get(1).getDecisions().size()); // no pruning
    assertEquals(1, result.get(2).getDecisions().size());
  }

  @Test
  public void test6a() {
    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(NameUsageSearchParameter.DECISION_MODE, "_NULL");

    index(test6_data());

    List<NameUsageWrapper> result = search(query).getResult();

    assertEquals(3, result.size());
    assertEquals("AAA", result.get(0).getId());
    assertEquals("BBB", result.get(1).getId());
    assertEquals("DDD", result.get(2).getId());
  }

  @Test
  public void test6b() {
    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(NameUsageSearchParameter.DECISION_MODE, "_NOT_NULL");

    index(test6_data());

    List<NameUsageWrapper> result = search(query).getResult();

    assertEquals(1, result.size());
    assertEquals("CCC", result.get(0).getId());
  }

  @Test
  public void test6c() {
    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(NameUsageSearchParameter.DECISION_MODE, "_NOT_NULL");
    
    // Makes no difference - catalog key ignored when generating query
    query.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, 1);

    index(test6_data());

    List<NameUsageWrapper> result = search(query).getResult();

    assertEquals(1, result.size());
    assertEquals("CCC", result.get(0).getId());
  }

  public List<NameUsageWrapper> test6_data() {
    SimpleDecision sd1 = new SimpleDecision();
    sd1.setId(1);
    sd1.setDatasetKey(101);
    sd1.setMode(null);
    NameUsageWrapper nuw1 = minimalTaxon();
    nuw1.setId("AAA");
    nuw1.setDecisions(List.of(sd1));

    SimpleDecision sd2 = new SimpleDecision();
    sd2.setId(2);
    sd2.setDatasetKey(201);
    sd2.setMode(null);
    NameUsageWrapper nuw2 = minimalTaxon();
    nuw2.setId("BBB");
    nuw2.setDecisions(List.of(sd2));

    SimpleDecision sd3 = new SimpleDecision();
    sd3.setId(3);
    sd3.setDatasetKey(103);
    sd3.setMode(Mode.UPDATE);
    NameUsageWrapper nuw3 = minimalTaxon();
    nuw3.setId("CCC");
    nuw3.setDecisions(List.of(sd3));

    NameUsageWrapper nuw4 = minimalTaxon();
    nuw4.setId("DDD");
    // no decisions at all

    return List.of(nuw1, nuw2, nuw3, nuw4);

  }

}
