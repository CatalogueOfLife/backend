package org.col.es;

import org.junit.Ignore;
import org.junit.Test;

//@Ignore("Embedded ES not working on jenkins yet")
public class EsUtilTest extends EsReadTestBase {

  @Test
  public void createAndDeleteIndices() throws EsException {
    EsUtil.deleteIndex(getEsClient(), "name_usage"); // OK if index does not exist
    EsUtil.createIndex(getEsClient(), "name_usage", getEsConfig().nameUsage);
    EsUtil.deleteIndex(getEsClient(), "name_usage");
  }

}
