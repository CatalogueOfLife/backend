package life.catalogue.es;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DSIDValue;
import life.catalogue.es.ddl.Analyzer;
import life.catalogue.es.nu.NameUsageWrapperConverter;

import java.io.IOException;

import org.elasticsearch.client.RestClient;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static life.catalogue.es.EsUtil.insert;
import static life.catalogue.es.EsUtil.refreshIndex;
import static org.junit.Assert.*;

public class EsUtilTest extends EsReadTestBase {

  private static RestClient client;

  @BeforeClass
  public static void init() {
    client = esSetupRule.getClient();
  }

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void testInsert() throws IOException {
    String id = insert(client, indexName(), new EsNameUsage());
    System.out.println("Generated id: " + id);
    assertNotNull(id);
  }

  @Test
  public void testCount() throws IOException {
    insert(client, indexName(), new EsNameUsage());
    insert(client, indexName(), new EsNameUsage());
    insert(client, indexName(), new EsNameUsage());
    insert(client, indexName(), new EsNameUsage());
    refreshIndex(client, indexName());
    assertEquals(4, EsUtil.count(client, indexName()));
  }

  @Test
  public void deleteDataset() throws IOException {
    // Insert 3 documents (overwriting dataset key to known values)
    NameUsageWrapperConverter transfer = new NameUsageWrapperConverter();
    EsNameUsage doc = transfer.toDocument(TestEntityGenerator.newNameUsageTaxonWrapper());
    doc.setDatasetKey(1);
    insert(client, indexName(), doc);
    doc = transfer.toDocument(TestEntityGenerator.newNameUsageSynonymWrapper());
    doc.setDatasetKey(1);
    insert(client, indexName(), doc);
    doc = transfer.toDocument(TestEntityGenerator.newNameUsageBareNameWrapper());
    doc.setDatasetKey(2);
    insert(client, indexName(), doc);
    refreshIndex(client, indexName());
    assertEquals(3, EsUtil.count(client, indexName()));

    int i = EsUtil.deleteDataset(client, indexName(), 1);
    assertEquals(2, i);
    refreshIndex(client, indexName());
    assertEquals(1, EsUtil.count(client, indexName()));

    i = EsUtil.deleteDataset(client, indexName(), 2);
    assertEquals(1, i);
    refreshIndex(client, indexName());
    assertEquals(0, EsUtil.count(client, indexName()));

    i = EsUtil.deleteDataset(client, indexName(), 3);
    assertEquals(0, i);
  }

  @Test
  public void testDeleteSector() throws IOException {
    // Insert 3 documents (overwriting sector key to known values)
    NameUsageWrapperConverter transfer = new NameUsageWrapperConverter();
    EsNameUsage doc = transfer.toDocument(TestEntityGenerator.newNameUsageTaxonWrapper());
    doc.setSectorKey(1);
    insert(client, indexName(), doc);
    doc = transfer.toDocument(TestEntityGenerator.newNameUsageSynonymWrapper());
    doc.setSectorKey(1);
    insert(client, indexName(), doc);
    doc = transfer.toDocument(TestEntityGenerator.newNameUsageBareNameWrapper());
    doc.setSectorKey(2);
    insert(client, indexName(), doc);
    refreshIndex(client, indexName());
    assertEquals(3, EsUtil.count(client, indexName()));

    DSIDValue<Integer> secKey = DSID.of(doc.getDatasetKey(), 1);
    int i = EsUtil.deleteSector(client, indexName(), secKey);
    assertEquals(2, i);
    refreshIndex(client, indexName());
    assertEquals(1, EsUtil.count(client, indexName()));

    i = EsUtil.deleteSector(client, indexName(), secKey.id(2));
    assertEquals(1, i);
    refreshIndex(client, indexName());
    assertEquals(0, EsUtil.count(client, indexName()));

    i = EsUtil.deleteSector(client, indexName(), secKey.id(3));
    assertEquals(0, i);
  }

  @Test
  public void indexExists() throws IOException {
    assertTrue(EsUtil.indexExists(client, indexName())); // we just created it in @Before
    EsUtil.deleteIndex(client, index());
    assertFalse(EsUtil.indexExists(client, indexName()));
  }

  @Test
  public void getSearchTerms() throws IOException {
    String[] terms = EsUtil.getSearchTerms(client, indexName(), Analyzer.AUTO_COMPLETE, "Rosy bee-eater");
    assertArrayEquals(new String[] {"rosy", "bee", "eater"}, terms);
    terms = EsUtil.getSearchTerms(client, indexName(), Analyzer.SCINAME_AUTO_COMPLETE, "Rosy bee-eater");
    assertArrayEquals(new String[] {"rosy", "beeeater"}, terms);
    terms = EsUtil.getSearchTerms(client, indexName(), Analyzer.SCINAME_AUTO_COMPLETE, "Ro,sy bee,eater");
    assertArrayEquals(new String[] {"ro", "sy", "bee", "eater"}, terms);
    terms = EsUtil.getSearchTerms(client, indexName(), Analyzer.SCINAME_AUTO_COMPLETE, "Acer nigrÜm × Açer saccharùm");
    assertArrayEquals(new String[] {"acer", "nigrum", "acer", "saccharum"}, terms);
  }

}
