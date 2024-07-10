package life.catalogue.es.nu;

import life.catalogue.common.io.TempFile;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.es.EsConfig;
import life.catalogue.es.EsException;
import life.catalogue.es.EsReadWriteTestBase;

import java.io.IOException;

import org.elasticsearch.client.RestClient;
import org.junit.Ignore;
import org.junit.Test;

@Ignore // Only for playing around with big datasets
public class IndexDatasetTest extends EsReadWriteTestBase {

  @Test
  public void indexDataset() throws IOException, EsException {
    try (RestClient client = EsReadWriteTestBase.esSetupRule.getClient()) {
      EsConfig config = EsReadWriteTestBase.esSetupRule.getEsConfig();
      NameUsageIndexServiceEs svc = new NameUsageIndexServiceEs(client, config, TempFile.directoryFile(), SqlSessionFactoryRule.getSqlSessionFactory());
      svc.indexDataset(1000);
    }
  }

}
