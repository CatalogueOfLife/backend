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
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/*
 * Also separately tests serde for payload field, which contains the serialized NameUsageWrapper object. NB Can't extend
 * SerdeTestBase b/c it's specifically about (de)serialization to ES documents, which uses another ObjectMapper.
 */
public class EsNameUsageSerde extends EsReadTestBase {

  static Logger LOG = LoggerFactory.getLogger(EsNameUsageSerde.class);

  @Test
  public void testTaxon() throws IOException {
    roundtrip(TestEntityGenerator.newNameUsageTaxonWrapper());
  }

  @Test
  public void testSynonym() throws IOException {
    roundtrip(TestEntityGenerator.newNameUsageSynonymWrapper());
  }

  @Test
  public void testBareName() throws IOException {
    roundtrip(TestEntityGenerator.newNameUsageBareNameWrapper());
  }

  static void roundtrip(NameUsageWrapper nuwIn) throws IOException {
    String json = EsModule.write(nuwIn);
    System.out.println(json);
    NameUsageWrapper nuwOut = EsModule.readNameUsageWrapper(json);
    assertEquals(nuwIn, nuwOut);
  }

  @Test
  public void testEsNameUsage() throws IOException {
    EsNameUsage docIn = new EsNameUsage();
    docIn.setPayload(EsModule.write(TestEntityGenerator.newNameUsageTaxonWrapper()));
    docIn.setAuthorshipComplete("John Smith");
    docIn.setDatasetKey(472);
    docIn.setNameFields(EnumSet.of(NameField.COMBINATION_EX_AUTHORS, NameField.UNINOMIAL));
    docIn.setNameId("16");
    docIn.setNameIndexIds(Set.of(56770));
    docIn.setPublishedInId("AMO333");
    docIn.setRank(Rank.SPECIES);
    docIn.setStatus(TaxonomicStatus.ACCEPTED);
    docIn.setType(NameType.SCIENTIFIC);
    docIn.setVernacularNames(Arrays.asList("Apple tree"));

    String json = EsModule.write(docIn);
    EsNameUsage docOut = EsModule.readDocument(json);
    assertEquals(docIn, docOut);
  }
}
