package org.col.es;

import org.col.db.PgSetupRule;
import org.elasticsearch.client.RestClient;
import org.junit.ClassRule;
import org.junit.rules.ExternalResource;

/**
 * Base class for tests that want to write to ES, which in practice means reading from postgress.
 * Therefore this base class extends DaoTestBase.
 */
public class EsReadWriteTestBase extends ExternalResource {
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule(false);
  
  @ClassRule
  public static EsSetupRule esSetupRule = new EsSetupRule();
  
  protected EsConfig getEsConfig() {
    return esSetupRule.getEsConfig();
  }
  
  protected RestClient getEsClient() {
    return esSetupRule.getEsClient();
  }
  
  @Override
  protected void before() throws Throwable {
    super.before();
  }
}
