package org.col.es;

import java.io.IOException;

import org.col.api.TestEntityGenerator;
import org.col.es.model.NameUsageDocument;
import org.col.es.name.index.NameUsageWrapperConverter;
import org.elasticsearch.client.RestClient;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.col.es.EsUtil.insert;
import static org.col.es.EsUtil.refreshIndex;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EsUtilTest extends EsReadTestBase {

  private static RestClient client;

  @BeforeClass
  public static void init() {
    client = esSetupRule.getEsClient();
  }

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void testInsert() throws IOException {
    String id = insert(client, indexName, new NameUsageDocument());
    System.out.println("Generated id: " + id);
    assertNotNull(id);
  }

  @Test
  public void testCount() throws IOException {
    insert(client, indexName, new NameUsageDocument());
    insert(client, indexName, new NameUsageDocument());
    insert(client, indexName, new NameUsageDocument());
    insert(client, indexName, new NameUsageDocument());
    refreshIndex(client, indexName);
    assertEquals(4, EsUtil.count(client, indexName));
  }

  @Test
  public void deleteDataset() throws IOException {
    // Insert 3 documents (overwriting dataset key to known values)
    NameUsageWrapperConverter transfer = new NameUsageWrapperConverter();
    NameUsageDocument enu = transfer.toDocument(TestEntityGenerator.newNameUsageTaxonWrapper());
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
    // Insert 3 documents (overwriting sector key to known values)
    NameUsageWrapperConverter transfer = new NameUsageWrapperConverter();
    NameUsageDocument enu = transfer.toDocument(TestEntityGenerator.newNameUsageTaxonWrapper());
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
    assertTrue(EsUtil.indexExists(client, indexName)); // we just created it in @Before
    EsUtil.deleteIndex(client, indexName);
    assertFalse(EsUtil.indexExists(client, indexName));
  }

}
