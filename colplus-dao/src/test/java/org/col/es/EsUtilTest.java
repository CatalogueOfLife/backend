package org.col.es;

import org.col.api.model.Taxon;
import org.junit.Test;

public class EsUtilTest extends EsReadTestBase {

  @Test
  public void createAndDeleteIndices() {
    EsUtil.deleteIndices(getEsConfig(), Taxon.class); // OK if index does not exist
    EsUtil.createIndices(getEsConfig(), Taxon.class);
    EsUtil.deleteIndices(getEsConfig(), Taxon.class);
  }

}
