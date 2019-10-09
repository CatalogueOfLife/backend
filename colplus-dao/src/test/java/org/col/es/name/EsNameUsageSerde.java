package org.col.es.name;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;

import org.col.api.TestEntityGenerator;
import org.col.api.search.NameUsageWrapper;
import org.col.api.vocab.NameField;
import org.col.api.vocab.TaxonomicStatus;
import org.col.es.EsModule;
import org.col.es.EsReadTestBase;
import org.col.es.model.NameUsageDocument;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;

/*
 * Also separately tests serde for payload field, which contains the serialized NameUsageWrapper object. NB Can't extend
 * SerdeTestBase b/c it's specifically about (de)serialization to ES documents, which uses another ObjectMapper.
 */
public class EsNameUsageSerde extends EsReadTestBase {

  static Logger LOG = LoggerFactory.getLogger(EsNameUsageSerde.class);

  @Test
  public void testTaxon1() throws IOException {
    NameUsageWrapper nuwIn = TestEntityGenerator.newNameUsageTaxonWrapper();
    String json = EsModule.write(nuwIn);
    LOG.debug(json);
    NameUsageWrapper nuwOut = EsModule.readNameUsageWrapper(json);
    assertEquals(nuwIn, nuwOut);
  }

  @Test
  public void testTaxon2() throws IOException {
    NameUsageWrapper nuwIn = TestEntityGenerator.newNameUsageTaxonWrapper();
    String json = EsModule.write(nuwIn);
    LOG.debug(json);
    NameUsageWrapper nuwOut = EsModule.readNameUsageWrapper(json);
    assertEquals(nuwIn, nuwOut);
  }

  @Test
  public void testSynonym1() throws IOException {
    NameUsageWrapper nuwIn = TestEntityGenerator.newNameUsageSynonymWrapper();
    String json = EsModule.write(nuwIn);
    LOG.debug(json);
    NameUsageWrapper nuwOut = EsModule.readNameUsageWrapper(json);
    assertEquals(nuwIn, nuwOut);
  }

  @Test
  public void testSynonym2() throws IOException {
    NameUsageWrapper nuwIn = TestEntityGenerator.newNameUsageSynonymWrapper();
    String json = EsModule.write(nuwIn);
    LOG.debug(json);
    NameUsageWrapper nuwOut = EsModule.readNameUsageWrapper(json);
    assertEquals(nuwIn, nuwOut);
  }

  @Test
  public void testBareName1() throws IOException {
    NameUsageWrapper nuwIn = TestEntityGenerator.newNameUsageBareNameWrapper();
    String json = EsModule.write(nuwIn);
    LOG.debug(json);
    NameUsageWrapper nuwOut = EsModule.readNameUsageWrapper(json);
    assertEquals(nuwIn, nuwOut);
  }

  @Test
  public void testBareName2() throws IOException {
    NameUsageWrapper nuwIn = TestEntityGenerator.newNameUsageBareNameWrapper();
    String json = EsModule.write(nuwIn);
    LOG.debug(json);
    NameUsageWrapper nuwOut = EsModule.readNameUsageWrapper(json);
    assertEquals(nuwIn, nuwOut);
  }

  @Test
  public void testEsNameUsage1() throws IOException {
    NameUsageDocument docIn = new NameUsageDocument();
    docIn.setPayload(EsModule.write(TestEntityGenerator.newNameUsageTaxonWrapper()));
    docIn.setAuthorship("John Smith");
    docIn.setDatasetKey(472);
    docIn.setNameFields(EnumSet.of(NameField.COMBINATION_EX_AUTHORS, NameField.UNINOMIAL));
    docIn.setNameId("16");
    docIn.setNameIndexId("afd56770af");
    docIn.setPublishedInId("AMO333");
    docIn.setRank(Rank.SPECIES);
    docIn.setStatus(TaxonomicStatus.ACCEPTED);
    docIn.setType(NameType.SCIENTIFIC);
    docIn.setVernacularNames(Arrays.asList("Apple tree"));

    String json = EsModule.write(docIn);
    NameUsageDocument docOut = EsModule.readDocument(json);
    assertEquals(docIn, docOut);

    NameUsageWrapper nuw = EsModule.readNameUsageWrapper(docOut.getPayload());
    assertEquals(TestEntityGenerator.newNameUsageTaxonWrapper(), nuw);

  }

  @Test
  public void testEsNameUsage2() throws IOException {
    NameUsageDocument docIn = new NameUsageDocument();
    docIn.setPayload(
        EsModule.write(TestEntityGenerator.newNameUsageTaxonWrapper()));
    docIn.setAuthorship("John Smith");
    docIn.setDatasetKey(472);
    docIn.setNameFields(EnumSet.of(NameField.COMBINATION_EX_AUTHORS, NameField.UNINOMIAL));
    docIn.setNameId("16");
    docIn.setNameIndexId("afd56770af");
    docIn.setPublishedInId("AMO333");
    docIn.setRank(Rank.SPECIES);
    docIn.setStatus(TaxonomicStatus.ACCEPTED);
    docIn.setType(NameType.SCIENTIFIC);
    docIn.setVernacularNames(Arrays.asList("Apple tree"));

    String json = EsModule.write(docIn);
    LOG.debug(json);

    NameUsageDocument docOut = EsModule.readDocument(json);
    assertEquals(docIn, docOut);

    NameUsageWrapper nuw = EsModule.readNameUsageWrapper(docOut.getPayload());
    assertEquals(TestEntityGenerator.newNameUsageTaxonWrapper(), nuw);
  }

}
