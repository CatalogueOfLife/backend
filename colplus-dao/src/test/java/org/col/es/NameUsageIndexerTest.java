package org.col.es;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.col.api.TestEntityGenerator;
import org.col.api.search.NameUsageWrapper;
import org.elasticsearch.client.RestClient;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NameUsageIndexerTest extends EsReadWriteTestBase {

  @Test
  public void indexBatch() throws IOException, EsException {
    String indexName = "name_usage_test";
    try (RestClient client = getEsClient()) {
      EsUtil.deleteIndex(client, indexName);
      EsUtil.createIndex(client, indexName, getEsConfig().nameUsage);
      List<NameUsageWrapper> docs =
          Arrays.asList(TestEntityGenerator.newNameUsageTaxonWrapper(),
              TestEntityGenerator.newNameUsageSynonymWrapper(),
              TestEntityGenerator.newNameUsageSynonymWrapper());
      NameUsageIndexer indexer = new NameUsageIndexer(getEsClient(), indexName);
      indexer.accept(docs);
      EsUtil.refreshIndex(client, indexName);
      assertEquals(3, EsUtil.count(client, indexName));
    }
  }
}
