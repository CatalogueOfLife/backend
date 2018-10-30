package org.col.es;

import org.junit.Test;

public class EsUtilTest extends EsReadTestBase {

  @Test
  public void createAndDeleteIndices() throws EsException {
    EsUtil.deleteIndex(getEsClient(), "name_usage_test"); // OK if index does not exist
    EsUtil.createIndex(getEsClient(), "name_usage_test", getEsConfig().nameUsage);
    EsUtil.deleteIndex(getEsClient(), "name_usage_test");
  }

}
