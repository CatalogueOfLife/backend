package org.col.es;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.col.api.model.Name;
import org.col.api.model.NameUsage;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.model.SimpleName;
import org.col.api.model.Taxon;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameUsageWrapper;
import org.elasticsearch.client.RestClient;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.col.api.search.NameSearchParameter.TAXON_ID;
import static org.col.es.EsUtil.insert;
import static org.col.es.EsUtil.refreshIndex;

import static org.junit.Assert.*;

public class NameSearchTestAllParamsTest extends EsReadTestBase {

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

  @Test
  public void testTaxonID1() throws IOException {
    SimpleName t1 = new SimpleName("1", null, null);
    SimpleName t2 = new SimpleName("2", null, null);
    SimpleName t3 = new SimpleName("3", null, null);
    SimpleName t4 = new SimpleName("4", null, null);
    SimpleName t5 = new SimpleName("5", null, null);
    SimpleName t6 = new SimpleName("6", null, null);
    NameUsage bogus = new Taxon();
    bogus.setName(new Name());
    NameUsageWrapper nuw1 = new NameUsageWrapper();
    nuw1.setClassification(Arrays.asList(t1, t2, t3));
    nuw1.setUsage(bogus);
    NameUsageWrapper nuw2 = new NameUsageWrapper();
    nuw2.setClassification(Arrays.asList(t2, t3, t4));
    nuw2.setUsage(bogus);
    NameUsageWrapper nuw3 = new NameUsageWrapper();
    nuw3.setClassification(Arrays.asList(t3, t4, t5));
    nuw3.setUsage(bogus);
    NameUsageWrapper nuw4 = new NameUsageWrapper();
    nuw4.setClassification(Arrays.asList(t4, t5, t6));
    nuw4.setUsage(bogus);
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
    NameUsage bogus = new Taxon();
    bogus.setName(new Name());
    NameUsageWrapper nuw1 = new NameUsageWrapper();
    nuw1.setClassification(Arrays.asList(t1, t2, t3));
    nuw1.setUsage(bogus);
    NameUsageWrapper nuw2 = new NameUsageWrapper();
    nuw2.setClassification(Arrays.asList(t2, t3, t4));
    nuw2.setUsage(bogus);
    NameUsageWrapper nuw3 = new NameUsageWrapper();
    nuw3.setClassification(Arrays.asList(t3, t4, t5));
    nuw3.setUsage(bogus);
    NameUsageWrapper nuw4 = new NameUsageWrapper();
    nuw4.setClassification(Arrays.asList(t4, t5, t6));
    nuw4.setUsage(bogus);
    NameUsageWrapper nuw5 = new NameUsageWrapper();
    nuw5.setClassification(Arrays.asList(t5, t6, t7));
    nuw5.setUsage(bogus);
    NameUsageWrapper nuw6 = new NameUsageWrapper();
    nuw6.setClassification(Arrays.asList(t6, t7, t8));
    nuw6.setUsage(bogus);
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

  }

}
