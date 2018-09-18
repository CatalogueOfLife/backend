package org.col.es;

import org.col.api.model.Taxon;
import org.junit.Test;

public class EsUtilTest extends EsReadTestBase {

  @Test
  public void createAndDeleteIndices() throws EsException {
    EsUtil.deleteIndex(getEsConfig(), Taxon.class); // OK if index does not exist
    EsUtil.createIndex(getEsConfig(), Taxon.class);
    EsUtil.deleteIndex(getEsConfig(), Taxon.class);
  }

}
