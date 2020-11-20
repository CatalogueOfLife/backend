package life.catalogue.es.nu.search;

import java.util.Arrays;
import java.util.List;

import life.catalogue.api.search.*;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import life.catalogue.api.model.EditorialDecision.Mode;
import life.catalogue.es.EsReadTestBase;
import life.catalogue.es.InvalidQueryException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DecisionQueriesTest extends EsReadTestBase {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchServiceTest.class);
  NameUsageSearchRequest query;

  @Before
  public void before() {
    destroyAndCreateIndex();
    query = new NameUsageSearchRequest();
  }

  @Test // catalog key only used from pruning - not as a filter
  public void test1() {
    query.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, 77);

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
    query.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, 2);

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

    List<NameUsageWrapper> result = search(query).getResult();
    // Make sure catalog key does NOT function as a document filter:
    assertEquals(2, result.size());
    assertNull(result.get(0).getDecisions());
    assertEquals(1, result.get(1).getDecisions().size());
    assertEquals(Mode.BLOCK, result.get(1).getDecisions().get(0).getMode());
  }

  List<NameUsageWrapper> test3Data() {
    List<NameUsageWrapper> nuws = createNameUsages(3);

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

    nuws.get(0).setDecisions(Arrays.asList(sd1, sd2a)); // no match
    nuws.get(1).setDecisions(Arrays.asList(sd2b)); // match
    nuws.get(2).setDecisions(Arrays.asList(sd2c, sd3)); // match

    return nuws;
  }

  @Test // catalogue key used both as filter and prune
  public void test3() {
    List<NameUsageWrapper> nuws = test3Data();
    index(nuws);

    query.addFilter(NameUsageSearchParameter.DECISION_MODE, NameUsageRequest.IS_NOT_NULL);
    query.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, 2);
    query.setSortBy(NameUsageSearchRequest.SortBy.NATIVE);
    List<NameUsageWrapper> result = search(query).getResult();
    assertEquals(3, result.size());
    assertEquals(1, result.get(0).getDecisions().size()); // Pruned to 1 decision
    assertEquals(1, result.get(1).getDecisions().size());
    assertEquals(1, result.get(2).getDecisions().size());

    query.clearFilter(NameUsageSearchParameter.DECISION_MODE);
    query.addFilter(NameUsageSearchParameter.DECISION_MODE, Mode.UPDATE);
    result = search(query).getResult();
    assertEquals(1, result.size());
    assertEquals(1, result.get(0).getDecisions().size()); // Pruned to 1 decision
    assertEquals(nuws.get(2).getId(), result.get(0).getUsage().getId());

    query.clearFilter(NameUsageSearchParameter.CATALOGUE_KEY);
    query.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, 3);
    result = search(query).getResult();
    assertEquals(1, result.size());
    assertEquals(1, result.get(0).getDecisions().size()); // Pruned to 1 decision
  }

  // catalogue key required
  @Test(expected = InvalidQueryException.class)
  public void test6a() {
    index(test6_data());
    query.addFilter(NameUsageSearchParameter.DECISION_MODE, "_NULL");
    search(query);
  }

  // catalogue key required
  @Test(expected = InvalidQueryException.class)
  public void test6b() {
    index(test6_data());
    query.addFilter(NameUsageSearchParameter.DECISION_MODE, "_NOT_NULL");
    search(query);
  }

  @Test
  public void test6c() {
    index(test6_data());
    // Tests for the presence of at least one decision
    query.addFilter(NameUsageSearchParameter.DECISION_MODE, "_NOT_NULL");
    query.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, 101);
    List<NameUsageWrapper> result = search(query).getResult();
    assertEquals(1, result.size()); // nuw4 does not have any decisions at all
  }

  @Test
  public void test6d() {
    index(test6_data());

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    // Tests for the presence of at least one decision
    query.addFilter(NameUsageSearchParameter.DECISION_MODE, "_NOT_NULL");
    // no decisions at all in that catalogue
    query.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, 1);

    List<NameUsageWrapper> result = search(query).getResult();
    assertEquals(0, result.size());

  }

  @Test // With mode null, search voor name usages NOT having the specified catalog key
  public void test6e() {
    NameUsageSearchRequest query = new NameUsageSearchRequest();
    // Tests for the presence of at least one decision
    query.addFilter(NameUsageSearchParameter.DECISION_MODE, "_NULL");

    query.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, 102);

    index(test6_data());

    List<NameUsageWrapper> result = search(query).getResult();
    assertEquals(3, result.size());

  }

  // multiple cat keys
  @Test(expected = InvalidQueryException.class)
  public void test6f() {
    NameUsageSearchRequest query = new NameUsageSearchRequest();
    // Tests for the presence of at least one decision
    query.addFilter(NameUsageSearchParameter.DECISION_MODE, "_NULL");

    query.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, 101);
    query.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, 102);

    index(test6_data());

    List<NameUsageWrapper> result = search(query).getResult();
    assertEquals(2, result.size());

  }

  public List<NameUsageWrapper> test6_data() {
    SimpleDecision sd1 = new SimpleDecision();
    sd1.setId(1);
    sd1.setDatasetKey(101);
    sd1.setMode(Mode.BLOCK);
    NameUsageWrapper nuw1 = minimalTaxon();
    nuw1.setId("AAA");
    nuw1.setDecisions(List.of(sd1));

    SimpleDecision sd2 = new SimpleDecision();
    sd2.setId(2);
    sd2.setDatasetKey(102);
    sd2.setMode(Mode.BLOCK);
    NameUsageWrapper nuw2 = minimalTaxon();
    nuw2.setId("BBB");
    nuw2.setDecisions(List.of(sd2));

    SimpleDecision sd3 = new SimpleDecision();
    sd3.setId(3);
    sd3.setDatasetKey(103);
    sd3.setMode(Mode.UPDATE);

    SimpleDecision sd4 = new SimpleDecision();
    sd4.setId(4);
    sd4.setDatasetKey(104);
    sd4.setMode(Mode.BLOCK);

    NameUsageWrapper nuw3 = minimalTaxon();
    nuw3.setId("CCC");
    nuw3.setDecisions(List.of(sd3, sd4));

    NameUsageWrapper nuw4 = minimalTaxon();
    nuw4.setId("DDD");
    // no decisions at all

    return List.of(nuw1, nuw2, nuw3, nuw4);

  }

}
