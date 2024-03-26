package life.catalogue.es.nu;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.NameField;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.es.EsModule;
import life.catalogue.es.EsNameUsage;
import life.catalogue.es.EsReadTestBase;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.util.EnumSet;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;

public class EsNameUsageSerdeTest extends EsReadTestBase {

  @Test
  public void testEsNameUsage() throws IOException {
    EsNameUsage docIn = new EsNameUsage();
    docIn.setPayload(NameUsageWrapperConverter.deflate(TestEntityGenerator.newNameUsageTaxonWrapper()));
    docIn.setAuthorshipComplete("John Smith");
    docIn.setDatasetKey(472);
    docIn.setNameFields(EnumSet.of(NameField.COMBINATION_EX_AUTHORS, NameField.UNINOMIAL));
    docIn.setNameId("16");
    docIn.setPublishedInId("AMO333");
    docIn.setRank(Rank.SPECIES);
    docIn.setStatus(TaxonomicStatus.ACCEPTED);
    docIn.setType(NameType.SCIENTIFIC);

    String json = EsModule.write(docIn);
    EsNameUsage docOut = EsModule.readDocument(json);
    assertEquals(docIn, docOut);
  }
}
