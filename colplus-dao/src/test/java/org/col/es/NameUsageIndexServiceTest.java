package org.col.es;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.TestEntityGenerator;
import org.col.api.search.NameUsageWrapper;
import org.col.db.PgSetupRule;
import org.elasticsearch.client.RestClient;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NameUsageIndexServiceTest extends EsReadWriteTestBase {

  @Test // Nice in combination with PgImportIT.testGsdGithub
  //@Ignore
  public void indexDataSet() throws IOException, EsException {
    try (RestClient client = getEsClient()) {
      NameUsageIndexService svc =
          new NameUsageIndexService(client, getEsConfig(), factory(), false);
      svc.indexDataset(1000);
    }
  }

  @Test
  @Ignore
  public void indexBulk() throws IOException, EsException {
    String indexName = "name_usage_test";
    try (RestClient client = getEsClient()) {
      EsUtil.deleteIndex(client, indexName);
      EsUtil.createIndex(client, indexName, getEsConfig().nameUsage);

      List<? extends NameUsageWrapper> docs =
          Arrays.asList(TestEntityGenerator.newNameUsageTaxonWrapper(),
              TestEntityGenerator.newNameUsageSynonymWrapper(),
              TestEntityGenerator.newNameUsageSynonymWrapper());

      NameUsageIndexService svc =
          new NameUsageIndexService(client, getEsConfig(), factory(), false);
      svc.indexBulk(indexName, docs);
      EsUtil.refreshIndex(client, indexName);
      assertEquals(3, EsUtil.count(client, indexName));
    }
  }

  private static SqlSessionFactory factory() {
    return PgSetupRule.getSqlSessionFactory();
  }

}
