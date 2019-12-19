package life.catalogue.es.name.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.Arrays;
import java.util.List;
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

public class CatalogKeyTest extends EsReadTestBase {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameSearchServiceTest.class);

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void test1() {

    SimpleDecision sd1 = new SimpleDecision();
    sd1.setKey(100);
    sd1.setDatasetKey(1);

    SimpleDecision sd2a = new SimpleDecision();
    sd2a.setKey(101);
    sd2a.setDatasetKey(2);

    SimpleDecision sd2b = new SimpleDecision();
    sd2b.setKey(102);
    sd2b.setDatasetKey(2);

    SimpleDecision sd2c = new SimpleDecision();
    sd2c.setKey(103);
    sd2c.setDatasetKey(2);

    SimpleDecision sd3 = new SimpleDecision();
    sd3.setKey(104);
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
    sd1.setKey(100);
    sd1.setDatasetKey(1);
    sd1.setMode(Mode.UPDATE);

    SimpleDecision sd2a = new SimpleDecision();
    sd2a.setKey(101);
    sd2a.setDatasetKey(2);
    sd2a.setMode(Mode.BLOCK);

    SimpleDecision sd2b = new SimpleDecision();
    sd2b.setKey(102);
    sd2b.setDatasetKey(2);
    sd2b.setMode(Mode.REVIEWED);

    SimpleDecision sd2c = new SimpleDecision();
    sd2c.setKey(103);
    sd2c.setDatasetKey(2);
    sd2c.setMode(Mode.UPDATE);

    SimpleDecision sd3 = new SimpleDecision();
    sd3.setKey(104);
    sd3.setDatasetKey(3);
    sd3.setMode(Mode.UPDATE);

    List<NameUsageWrapper> nuws = createNameUsages(3);
    nuws.get(0).setDecisions(Arrays.asList(sd1)); // Won't match the query (wrong catalog key)
    nuws.get(1).setDecisions(Arrays.asList(sd2a, sd2b, sd2c)); // match
    nuws.get(2).setDecisions(Arrays.asList(sd3)); // match
    index(nuws);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(NameUsageSearchParameter.DECISION_MODE, Mode.UPDATE);
    query.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, 2);
    query.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, 3);
    List<NameUsageWrapper> result = search(query).getResult();

    /*
     * Now we have a decision mode in the search request, so catalogue key functions like any other parameter.
     */

    assertEquals(2, result.size());
    assertEquals(1,result.get(0).getDecisions().size()); // Pruned to 1 decision
    assertEquals(1, result.get(1).getDecisions().size());
  }

  @Test
  public void test4() {
    
    SimpleDecision sd1 = new SimpleDecision();
    sd1.setKey(100);
    sd1.setDatasetKey(1);
    sd1.setMode(Mode.UPDATE);

    SimpleDecision sd2a = new SimpleDecision();
    sd2a.setKey(101);
    sd2a.setDatasetKey(2);
    sd2a.setMode(Mode.BLOCK);

    SimpleDecision sd2b = new SimpleDecision();
    sd2b.setKey(102);
    sd2b.setDatasetKey(2);
    sd2b.setMode(Mode.REVIEWED);

    SimpleDecision sd2c = new SimpleDecision();
    sd2c.setKey(103);
    sd2c.setDatasetKey(2);
    sd2c.setMode(Mode.UPDATE);

    SimpleDecision sd3 = new SimpleDecision();
    sd3.setKey(104);
    sd3.setDatasetKey(3);
    sd3.setMode(Mode.UPDATE);

    List<NameUsageWrapper> nuws = createNameUsages(3);
    nuws.get(0).setDecisions(Arrays.asList(sd1)); // Won't match the query (wrong catalog key)
    nuws.get(1).setDecisions(Arrays.asList(sd2a, sd2b, sd2c)); // match
    nuws.get(2).setDecisions(Arrays.asList(sd3)); // match
    index(nuws);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(NameUsageSearchParameter.DECISION_MODE, Mode.UPDATE);
    query.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, 2);
    query.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, 3);
    List<NameUsageWrapper> result = search(query).getResult();

    /*
     * Now we have a decision mode in the search request, so catalogue key functions like any other parameter.
     */

    assertEquals(2, result.size());
    assertEquals(1,result.get(0).getDecisions().size()); // Pruned to 1 decision
    assertEquals(1, result.get(1).getDecisions().size());
  }

  @Test
  public void test5() {
    
    SimpleDecision sd1 = new SimpleDecision();
    sd1.setKey(100);
    sd1.setDatasetKey(1);
    sd1.setMode(Mode.UPDATE);

    SimpleDecision sd2a = new SimpleDecision();
    sd2a.setKey(101);
    sd2a.setDatasetKey(2);
    sd2a.setMode(Mode.BLOCK);

    SimpleDecision sd2b = new SimpleDecision();
    sd2b.setKey(102);
    sd2b.setDatasetKey(2);
    sd2b.setMode(Mode.REVIEWED);

    SimpleDecision sd2c = new SimpleDecision();
    sd2c.setKey(103);
    sd2c.setDatasetKey(2);
    sd2c.setMode(Mode.UPDATE);

    SimpleDecision sd3 = new SimpleDecision();
    sd3.setKey(104);
    sd3.setDatasetKey(3);
    sd3.setMode(Mode.UPDATE);

    List<NameUsageWrapper> nuws = createNameUsages(3);
    nuws.get(0).setDecisions(Arrays.asList(sd1)); // Won't match the query (wrong catalog key)
    nuws.get(1).setDecisions(Arrays.asList(sd2a, sd2b, sd2c)); // match
    nuws.get(2).setDecisions(Arrays.asList(sd3)); // match
    index(nuws);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(NameUsageSearchParameter.DECISION_MODE, Mode.UPDATE);
    List<NameUsageWrapper> result = search(query).getResult();

    /*
     * Now we JUST have a decision mode in the search request, so this behaves like any other query (no pruning afterwards)
     */
    assertEquals(3, result.size());
    assertEquals(1,result.get(0).getDecisions().size());
    assertEquals(3, result.get(1).getDecisions().size()); // no pruning
    assertEquals(1, result.get(2).getDecisions().size());
  }

}
