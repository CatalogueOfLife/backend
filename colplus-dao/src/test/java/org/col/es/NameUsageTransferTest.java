package org.col.es;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.col.api.TestEntityGenerator;
import org.col.es.model.EsNameUsage;
import org.junit.Ignore;
import org.junit.Test;

public class NameUsageTransferTest extends EsReadTestBase {

  @Test
  @Ignore
  @SuppressWarnings("unused")
  public void test() throws JsonProcessingException {
    NameUsageTransfer transfer = new NameUsageTransfer();
    EsNameUsage enu1 = transfer.toEsDocument(TestEntityGenerator.newNameUsageTaxonWrapper());
    EsNameUsage enu2 = transfer.toEsDocument(TestEntityGenerator.newNameUsageSynonymWrapper());
    EsNameUsage enu3 = transfer.toEsDocument(TestEntityGenerator.newNameUsageBareNameWrapper());
  }

}
