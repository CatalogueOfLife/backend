package life.catalogue.es.name.index;

import java.io.IOException;

import life.catalogue.db.PgSetupRule;
import life.catalogue.es.EsConfig;
import life.catalogue.es.EsException;
import life.catalogue.es.EsReadWriteTestBase;
import org.elasticsearch.client.RestClient;
import org.junit.Ignore;
import org.junit.Test;

@Ignore // Only for playing around with big datasets
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
