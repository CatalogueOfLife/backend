package org.col.es;

import org.elasticsearch.client.RestClient;
import org.junit.ClassRule;

/**
 * Base class for tests that only read from ES. Does not provide postgres functionality and saves
 * setup/initialization time accordingly.
 *
 */
public class EsReadTestBase {

  @ClassRule
  public static EsSetupRule esSetupRule = new EsSetupRule();

  protected EsConfig getEsConfig() {
    return esSetupRule.getEsConfig();
  }

  protected RestClient getEsClient() {
    return esSetupRule.getClientFactory().createClient();
  }

}
