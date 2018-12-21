package org.col.es;

import java.io.IOException;

import org.col.api.TestEntityGenerator;
import org.col.es.model.EsNameUsage;
import org.junit.Test;

public class NameUsageTransferTest {

  @Test
  @SuppressWarnings("unused")
  public void test() throws IOException {
    // Just make sure we have no (JSON) exceptions
    NameUsageTransfer transfer = new NameUsageTransfer();
    EsNameUsage enu1 = transfer.toDocument(TestEntityGenerator.newNameUsageTaxonWrapper());
    EsNameUsage enu2 = transfer.toDocument(TestEntityGenerator.newNameUsageSynonymWrapper());
    EsNameUsage enu3 = transfer.toDocument(TestEntityGenerator.newNameUsageBareNameWrapper());
  }

}
