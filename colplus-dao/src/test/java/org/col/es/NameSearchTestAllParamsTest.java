package org.col.es;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.col.api.model.Name;
import org.col.api.model.NameUsage;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.model.SimpleName;
import org.col.api.model.Taxon;
import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameUsageWrapper;
import org.elasticsearch.client.RestClient;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.api.search.NameSearchParameter.*;
import static org.col.api.search.NameSearchParameter.TAXON_ID;
import static org.col.es.EsUtil.insert;
import static org.col.es.EsUtil.refreshIndex;
import static org.junit.Assert.assertEquals;

public class NameSearchTestAllParamsTest extends EsReadTestBase {

  private static final Logger LOG = LoggerFactory.getLogger(NameSearchTestAllParamsTest.class);
  private static EnumSet<NameSearchParameter> tested = EnumSet.noneOf(NameSearchParameter.class);

  private static final String indexName = "name_usage_test";

  private static RestClient client;
  private static NameUsageSearchService svc;

  @BeforeClass
  public static void init() {
    client = esSetupRule.getEsClient();
    svc = new NameUsageSearchService(indexName, esSetupRule.getEsClient());
  }

  @AfterClass
  public static void shutdown() throws IOException {
    // EsUtil.deleteIndex(client, indexName);
    client.close();
  }

  @Before
  public void before() throws IOException {
    EsUtil.deleteIndex(client, indexName);
    EsUtil.createIndex(client, indexName, getEsConfig().nameUsage);
  }

  /*
   * Create a minimalistic NameUsageWrapper - just enough to allow it to be indexed without NPEs & stuff.
   */
  private static NameUsageWrapper minimalNameUsage() {
    NameUsage bogus = new Taxon();
    bogus.setName(new Name());
    NameUsageWrapper nuw = new NameUsageWrapper();
    nuw.setUsage(bogus);
    return nuw;
  }

  @Test
  public void testTaxonID1() throws IOException {

    SimpleName t1 = new SimpleName("1", null, null);
    SimpleName t2 = new SimpleName("2", null, null);
    SimpleName t3 = new SimpleName("3", null, null);
    SimpleName t4 = new SimpleName("4", null, null);
    SimpleName t5 = new SimpleName("5", null, null);
    SimpleName t6 = new SimpleName("6", null, null);

    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.setClassification(Arrays.asList(t1, t2, t3));
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.setClassification(Arrays.asList(t2, t3, t4));
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.setClassification(Arrays.asList(t3, t4, t5));
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.setClassification(Arrays.asList(t4, t5, t6));
    NameUsageTransfer transfer = new NameUsageTransfer();

    insert(client, indexName, transfer.toDocument(nuw1));
    insert(client, indexName, transfer.toDocument(nuw2));
    insert(client, indexName, transfer.toDocument(nuw3));
    insert(client, indexName, transfer.toDocument(nuw4));
    refreshIndex(client, indexName);

    NameSearchRequest req = new NameSearchRequest();
    req.addFilter(TAXON_ID, "3");

    /*
     * Yikes - again, remember to resurrect the expected result, because NameUsageWrappers will get pruned on insert !!!
     */
    nuw1.setClassification(Arrays.asList(t1, t2, t3));
    nuw2.setClassification(Arrays.asList(t2, t3, t4));
    nuw3.setClassification(Arrays.asList(t3, t4, t5));
    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw3);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, req, new Page());
    assertEquals(expected, result.getResult());

    countdown(TAXON_ID);

  }

  @Test
  public void testTaxonID2() throws IOException {
    SimpleName t1 = new SimpleName("1", null, null);
    SimpleName t2 = new SimpleName("2", null, null);
    SimpleName t3 = new SimpleName("3", null, null);
    SimpleName t4 = new SimpleName("4", null, null);
    SimpleName t5 = new SimpleName("5", null, null);
    SimpleName t6 = new SimpleName("6", null, null);
    SimpleName t7 = new SimpleName("7", null, null);
    SimpleName t8 = new SimpleName("8", null, null);

    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.setClassification(Arrays.asList(t1, t2, t3));
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.setClassification(Arrays.asList(t2, t3, t4));
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.setClassification(Arrays.asList(t3, t4, t5));
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.setClassification(Arrays.asList(t4, t5, t6));
    NameUsageWrapper nuw5 = minimalNameUsage();
    nuw5.setClassification(Arrays.asList(t5, t6, t7));
    NameUsageWrapper nuw6 = minimalNameUsage();
    nuw6.setClassification(Arrays.asList(t6, t7, t8));

    NameUsageTransfer transfer = new NameUsageTransfer();

    insert(client, indexName, transfer.toDocument(nuw1));
    insert(client, indexName, transfer.toDocument(nuw2));
    insert(client, indexName, transfer.toDocument(nuw3));
    insert(client, indexName, transfer.toDocument(nuw4));
    refreshIndex(client, indexName);

    NameSearchRequest req = new NameSearchRequest();
    req.addFilter(TAXON_ID, "4");
    req.addFilter(TAXON_ID, "5");

    /*
     * Yikes - again, remember to resurrect the expected result, because NameUsageWrappers will get pruned on insert !!!
     */
    nuw2.setClassification(Arrays.asList(t2, t3, t4));
    nuw3.setClassification(Arrays.asList(t3, t4, t5));
    nuw4.setClassification(Arrays.asList(t4, t5, t6));
    List<NameUsageWrapper> expected = Arrays.asList(nuw2, nuw3, nuw4);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, req, new Page());
    assertEquals(expected, result.getResult());

    countdown(TAXON_ID);

  }

  @Test
  public void testPublisherKey1() throws IOException {
    NameUsage bogus = new Taxon();
    bogus.setName(new Name());
    UUID uuid1 = UUID.randomUUID();
    UUID uuid2 = UUID.randomUUID();
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.setPublisherKey(uuid1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.setPublisherKey(uuid1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.setPublisherKey(uuid2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.setPublisherKey(null);

    NameUsageTransfer transfer = new NameUsageTransfer();

    insert(client, indexName, transfer.toDocument(nuw1));
    insert(client, indexName, transfer.toDocument(nuw2));
    insert(client, indexName, transfer.toDocument(nuw3));
    insert(client, indexName, transfer.toDocument(nuw4));
    refreshIndex(client, indexName);

    NameSearchRequest req = new NameSearchRequest();
    req.addFilter(PUBLISHER_KEY, uuid1);

    /*
     * Yikes - again, remember to resurrect the expected result, because NameUsageWrappers will get pruned on insert !!!
     */
    nuw1.setPublisherKey(uuid1);
    nuw2.setPublisherKey(uuid1);
    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, req, new Page());
    assertEquals(expected, result.getResult());

    countdown(PUBLISHER_KEY);

  }

  @Test
  public void testPublisherKey2() throws IOException {
    NameUsage bogus = new Taxon();
    bogus.setName(new Name());
    UUID uuid1 = UUID.randomUUID();
    UUID uuid2 = UUID.randomUUID();
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.setPublisherKey(uuid1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.setPublisherKey(uuid1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.setPublisherKey(uuid2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.setPublisherKey(null);

    NameUsageTransfer transfer = new NameUsageTransfer();

    insert(client, indexName, transfer.toDocument(nuw1));
    insert(client, indexName, transfer.toDocument(nuw2));
    insert(client, indexName, transfer.toDocument(nuw3));
    insert(client, indexName, transfer.toDocument(nuw4));
    refreshIndex(client, indexName);

    NameSearchRequest req = new NameSearchRequest();
    req.addFilter(PUBLISHER_KEY, NameSearchRequest.NULL_VALUE);

    List<NameUsageWrapper> expected = Arrays.asList(nuw4);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, req, new Page());
    assertEquals(expected, result.getResult());

    countdown(PUBLISHER_KEY);

  }

  @Test
  public void testPublisherKey3() throws IOException {
    NameUsage bogus = new Taxon();
    bogus.setName(new Name());
    UUID uuid1 = UUID.randomUUID();
    UUID uuid2 = UUID.randomUUID();
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.setPublisherKey(uuid1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.setPublisherKey(uuid1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.setPublisherKey(uuid2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.setPublisherKey(null);

    NameUsageTransfer transfer = new NameUsageTransfer();

    insert(client, indexName, transfer.toDocument(nuw1));
    insert(client, indexName, transfer.toDocument(nuw2));
    insert(client, indexName, transfer.toDocument(nuw3));
    insert(client, indexName, transfer.toDocument(nuw4));
    refreshIndex(client, indexName);

    NameSearchRequest req = new NameSearchRequest();
    req.addFilter(PUBLISHER_KEY, NameSearchRequest.NULL_VALUE);

    List<NameUsageWrapper> expected = Arrays.asList(nuw4);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, req, new Page());
    assertEquals(expected, result.getResult());

    countdown(PUBLISHER_KEY);

  }

  @Test
  public void testPublisherKey4() throws IOException {
    NameUsage bogus = new Taxon();
    bogus.setName(new Name());
    UUID uuid1 = UUID.randomUUID();
    UUID uuid2 = UUID.randomUUID();
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.setPublisherKey(uuid1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.setPublisherKey(uuid1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.setPublisherKey(uuid2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.setPublisherKey(null);

    NameUsageTransfer transfer = new NameUsageTransfer();

    insert(client, indexName, transfer.toDocument(nuw1));
    insert(client, indexName, transfer.toDocument(nuw2));
    insert(client, indexName, transfer.toDocument(nuw3));
    insert(client, indexName, transfer.toDocument(nuw4));
    refreshIndex(client, indexName);

    NameSearchRequest req = new NameSearchRequest();
    req.addFilter(PUBLISHER_KEY, NameSearchRequest.NOT_NULL_VALUE);

    nuw1.setPublisherKey(uuid1);
    nuw2.setPublisherKey(uuid1);
    nuw3.setPublisherKey(uuid2);
    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw3);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, req, new Page());
    assertEquals(expected, result.getResult());

    countdown(PUBLISHER_KEY);

  }

  @Test
  public void testDecisionKey1() throws IOException {
    NameUsage bogus = new Taxon();
    bogus.setName(new Name());
    Integer key1 = 100;
    Integer key2 = 101;
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.setDecisionKey(key1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.setDecisionKey(key1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.setDecisionKey(key2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.setDecisionKey(null);

    NameUsageTransfer transfer = new NameUsageTransfer();

    insert(client, indexName, transfer.toDocument(nuw1));
    insert(client, indexName, transfer.toDocument(nuw2));
    insert(client, indexName, transfer.toDocument(nuw3));
    insert(client, indexName, transfer.toDocument(nuw4));
    refreshIndex(client, indexName);

    NameSearchRequest req = new NameSearchRequest();
    req.addFilter(DECISION_KEY, key1);

    nuw1.setDecisionKey(key1);
    nuw2.setDecisionKey(key1);
    nuw3.setDecisionKey(key2);
    nuw4.setDecisionKey(null);
    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, req, new Page());
    assertEquals(expected, result.getResult());

    countdown(DECISION_KEY);

  }

  @Test
  public void testDecisionKey2() throws IOException {
    NameUsage bogus = new Taxon();
    bogus.setName(new Name());
    Integer key1 = 100;
    Integer key2 = 101;
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.setDecisionKey(key1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.setDecisionKey(key1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.setDecisionKey(key2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.setDecisionKey(null);

    NameUsageTransfer transfer = new NameUsageTransfer();

    insert(client, indexName, transfer.toDocument(nuw1));
    insert(client, indexName, transfer.toDocument(nuw2));
    insert(client, indexName, transfer.toDocument(nuw3));
    insert(client, indexName, transfer.toDocument(nuw4));
    refreshIndex(client, indexName);

    NameSearchRequest req = new NameSearchRequest();
    req.addFilter(DECISION_KEY, NameSearchRequest.NOT_NULL_VALUE);

    nuw1.setDecisionKey(key1);
    nuw2.setDecisionKey(key1);
    nuw3.setDecisionKey(key2);
    nuw4.setDecisionKey(null);
    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw3);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, req, new Page());
    assertEquals(expected, result.getResult());

    countdown(DECISION_KEY);

  }

  @Test
  public void testDecisionKey3() throws IOException {
    NameUsage bogus = new Taxon();
    bogus.setName(new Name());
    Integer key1 = 100;
    Integer key2 = 101;
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.setDecisionKey(key1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.setDecisionKey(key1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.setDecisionKey(key2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.setDecisionKey(null);

    NameUsageTransfer transfer = new NameUsageTransfer();

    insert(client, indexName, transfer.toDocument(nuw1));
    insert(client, indexName, transfer.toDocument(nuw2));
    insert(client, indexName, transfer.toDocument(nuw3));
    insert(client, indexName, transfer.toDocument(nuw4));
    refreshIndex(client, indexName);

    NameSearchRequest req = new NameSearchRequest();
    req.addFilter(DECISION_KEY, NameSearchRequest.NULL_VALUE);

    nuw1.setDecisionKey(key1);
    nuw2.setDecisionKey(key1);
    nuw3.setDecisionKey(key2);
    nuw4.setDecisionKey(null);
    List<NameUsageWrapper> expected = Arrays.asList(nuw4);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, req, new Page());
    assertEquals(expected, result.getResult());

    countdown(DECISION_KEY);

  }

  private static void countdown(NameSearchParameter param) {
    tested.add(param);
    LOG.info("####  Name search parameter {} unit tested  ####", param);
    if (tested.size() == NameSearchParameter.values().length) {
      LOG.info("####  All name search parameters tested!  ####");
    } else {
      LOG.info("####  {} parameter(s) tested. {} more to go  ####", tested.size(), NameSearchParameter.values().length - tested.size());
    }
  }

}
