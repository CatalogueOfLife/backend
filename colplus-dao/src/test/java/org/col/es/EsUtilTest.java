package org.col.es;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.col.api.TestEntityGenerator;
import org.col.es.model.EsNameUsage;
import org.elasticsearch.client.RestClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.col.es.EsUtil.insert;
import static org.col.es.EsUtil.refreshIndex;
import static org.junit.Assert.assertEquals;

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
  public void createAndDeleteIndices() throws EsException {
    EsUtil.deleteIndex(client, indexName); // OK if index does not exist
    EsUtil.createIndex(client, indexName, getEsConfig().nameUsage);
    EsUtil.deleteIndex(client, indexName);
  }

  @Test
  public void deleteDataset() throws JsonProcessingException {
    EsUtil.deleteIndex(client, indexName);
    EsUtil.createIndex(client, indexName, getEsConfig().nameUsage);
    // Insert 3 documents (overwriting dataset key to know values)
    NameUsageTransfer transfer = new NameUsageTransfer();
    EsNameUsage enu = transfer.toEsDocument(TestEntityGenerator.newNameUsageTaxonWrapper());
    enu.setDatasetKey(1);
    insert(client, indexName, enu);
    enu = transfer.toEsDocument(TestEntityGenerator.newNameUsageSynonymWrapper());
    enu.setDatasetKey(1);
    insert(client, indexName, enu);
    enu = transfer.toEsDocument(TestEntityGenerator.newNameUsageBareNameWrapper());
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

}
