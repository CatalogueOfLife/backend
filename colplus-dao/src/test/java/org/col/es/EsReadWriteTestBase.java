package org.col.es;

import org.col.db.dao.DaoTestBase;
import org.elasticsearch.client.RestClient;
import org.junit.ClassRule;

/**
 * Base class for tests that want to write to ES, which in practice means reading from postgress.
 * Therefore this base class extends DaoTestBase.
 */
public class EsReadWriteTestBase extends DaoTestBase {

  @ClassRule
  public static EsSetupRule esSetupRule = new EsSetupRule();

  protected EsConfig getEsConfig() {
    return esSetupRule.getEsConfig();
  }

  protected RestClient getEsClient() {
    return esSetupRule.getClientFactory().createClient();
  }

}
