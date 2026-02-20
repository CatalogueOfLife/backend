package life.catalogue.es;

import life.catalogue.common.io.TempFile;
import life.catalogue.config.EsConfig;
import life.catalogue.junit.SqlSessionFactoryRule;

import java.io.IOException;

import org.junit.Ignore;
import org.junit.jupiter.api.Disabled;
import org.junit.Test;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

@Disabled @Ignore // Only for playing around with big datasets
public class IndexDatasetTest extends EsReadWriteTestBase {

  @Test
  public void indexDataset() throws IOException, EsException {
    ElasticsearchClient client = EsReadWriteTestBase.esSetupRule.getClient();
    EsConfig config = EsReadWriteTestBase.esSetupRule.getEsConfig();
    NameUsageIndexServiceEs svc = new NameUsageIndexServiceEs(client, config, TempFile.directoryFile(), SqlSessionFactoryRule.getSqlSessionFactory());
    svc.indexDataset(1000);
  }

}
