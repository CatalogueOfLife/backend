package org.col.es.name.index;

import java.io.IOException;

import org.col.db.PgSetupRule;
import org.col.es.EsConfig;
import org.col.es.EsException;
import org.col.es.EsReadWriteTestBase;
import org.elasticsearch.client.RestClient;
import org.junit.Ignore;
import org.junit.Test;

//@Ignore // really only to play around with substantial amounts of data
public class IndexDatasetTest extends EsReadWriteTestBase {

  @Test
  public void indexDataSet() throws IOException, EsException {
    try (RestClient client = EsReadWriteTestBase.esSetupRule.getEsClient()) {
      EsConfig config = EsReadWriteTestBase.esSetupRule.getEsConfig();
      NameUsageIndexServiceEs svc = new NameUsageIndexServiceEs(client, config, PgSetupRule.getSqlSessionFactory());
      svc.indexDataset(1000);
    }
  }

}
