package org.col.es;

import java.io.IOException;

import org.apache.ibatis.session.SqlSessionFactory;
import org.col.db.PgSetupRule;
import org.elasticsearch.client.RestClient;
import org.junit.Ignore;
import org.junit.Test;

public class NameUsageIndexServiceESTest extends EsReadWriteTestBase {

  @Test // Nice in combination with PgImportIT.testGsdGithub
  @Ignore
  public void indexDataSet() throws IOException, EsException {
    try (RestClient client = getEsClient()) {
      NameUsageIndexServiceES svc = new NameUsageIndexServiceES(client, getEsConfig(), factory());
      svc.indexDataset(1000);
    }
  }

   private static SqlSessionFactory factory() {
    return PgSetupRule.getSqlSessionFactory();
  }

}
