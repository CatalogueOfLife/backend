package org.col.es;

import java.io.IOException;

import org.col.api.TestEntityGenerator;
import org.col.es.model.EsNameUsage;
import org.elasticsearch.client.RestClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.col.es.EsUtil.insert;
import static org.col.es.EsUtil.refreshIndex;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EsUtilTest extends EsReadTestBase {

  private static final String indexName = "name_usage_test";

  private static RestClient client;

  @BeforeClass
  public static void init() {
    client = esSetupRule.getEsClient();
  }

  @AfterClass
  public static void shutdown() throws IOException {
    EsUtil.deleteIndex(client, indexName);
    client.close();
  }

  @Test
  public void createAndDeleteIndices() throws IOException {
    EsUtil.deleteIndex(client, indexName); // OK if index does not exist
    EsUtil.createIndex(client, indexName, getEsConfig().nameUsage);
    EsUtil.deleteIndex(client, indexName);
  }

  @Test
  public void deleteDataset() throws IOException {
    EsUtil.deleteIndex(client, indexName);
    EsUtil.createIndex(client, indexName, getEsConfig().nameUsage);
    // Insert 3 documents (overwriting dataset key to known values)
    NameUsageTransfer transfer = new NameUsageTransfer();
    EsNameUsage enu = transfer.toDocument(TestEntityGenerator.newNameUsageTaxonWrapper());
    enu.setDatasetKey(1);
    insert(client, indexName, enu);
    enu = transfer.toDocument(TestEntityGenerator.newNameUsageSynonymWrapper());
    enu.setDatasetKey(1);
    insert(client, indexName, enu);
    enu = transfer.toDocument(TestEntityGenerator.newNameUsageBareNameWrapper());
    enu.setDatasetKey(2);
    insert(client, indexName, enu);
    refreshIndex(client, indexName);
    assertEquals(3, EsUtil.count(client, indexName));

    int i = EsUtil.deleteDataset(client, indexName, 1);
    assertEquals(2, i);
    refreshIndex(client, indexName);
    assertEquals(1, EsUtil.count(client, indexName));

    i = EsUtil.deleteDataset(client, indexName, 2);
    assertEquals(1, i);
    refreshIndex(client, indexName);
    assertEquals(0, EsUtil.count(client, indexName));

    i = EsUtil.deleteDataset(client, indexName, 3);
    assertEquals(0, i);
  }

  @Test
  public void testDeleteSector() throws IOException {
    EsUtil.deleteIndex(client, indexName);
    EsUtil.createIndex(client, indexName, getEsConfig().nameUsage);
    // Insert 3 documents (overwriting sector key to known values)
    NameUsageTransfer transfer = new NameUsageTransfer();
    EsNameUsage enu = transfer.toDocument(TestEntityGenerator.newNameUsageTaxonWrapper());
    enu.setSectorKey(1);
    insert(client, indexName, enu);
    enu = transfer.toDocument(TestEntityGenerator.newNameUsageSynonymWrapper());
    enu.setSectorKey(1);
    insert(client, indexName, enu);
    enu = transfer.toDocument(TestEntityGenerator.newNameUsageBareNameWrapper());
    enu.setSectorKey(2);
    insert(client, indexName, enu);
    refreshIndex(client, indexName);
    assertEquals(3, EsUtil.count(client, indexName));

    int i = EsUtil.deleteSector(client, indexName, 1);
    assertEquals(2, i);
    refreshIndex(client, indexName);
    assertEquals(1, EsUtil.count(client, indexName));

    i = EsUtil.deleteSector(client, indexName, 2);
    assertEquals(1, i);
    refreshIndex(client, indexName);
    assertEquals(0, EsUtil.count(client, indexName));

    i = EsUtil.deleteSector(client, indexName, 3);
    assertEquals(0, i);
  }

  @Test
  public void indexExists() throws IOException {
    EsUtil.deleteIndex(client, indexName);
    EsUtil.createIndex(client, indexName, getEsConfig().nameUsage);
    assertTrue(EsUtil.indexExists(client, indexName));
    EsUtil.deleteIndex(client, indexName);
    assertFalse(EsUtil.indexExists(client, indexName));
  }

}
