package org.col.es.name;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

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
 * Also separately tests serde for payload field, which contains the serialized NameUsageWrapper
 * object. NB Can't extend SerdeTestBase b/c it's specifically about (de)serialization to ES
 * documents, which uses another ObjectMapper.
 */
public class EsNameUsageSerde extends EsReadTestBase {

  static Logger LOG = LoggerFactory.getLogger(EsNameUsageSerde.class);
  
  static final ObjectReader PAYLOAD_READER = EsModule.NAME_USAGE_READER;
  static final ObjectWriter PAYLOAD_WRITER = EsModule.NAME_USAGE_WRITER;

  @Test
  public void testTaxon1() throws IOException {
    NameUsageWrapper nuwIn = TestEntityGenerator.newNameUsageTaxonWrapper();
    String json = PAYLOAD_WRITER.writeValueAsString(nuwIn);
    LOG.debug(json);
    NameUsageWrapper nuwOut = PAYLOAD_READER.readValue(json);
    assertEquals(nuwIn, nuwOut);
  }

  @Test
  public void testTaxon2() throws IOException {
    NameUsageWrapper nuwIn = TestEntityGenerator.newNameUsageTaxonWrapper();
    String json = PAYLOAD_WRITER.writeValueAsString(nuwIn);
    LOG.debug(json);
    NameUsageWrapper nuwOut = PAYLOAD_READER.readValue(json);
    assertEquals(nuwIn, nuwOut);
  }

  @Test
  public void testSynonym1() throws IOException {
    NameUsageWrapper nuwIn = TestEntityGenerator.newNameUsageSynonymWrapper();
    String json = PAYLOAD_WRITER.writeValueAsString(nuwIn);
    LOG.debug(json);
    NameUsageWrapper nuwOut = PAYLOAD_READER.readValue(json);
    assertEquals(nuwIn, nuwOut);
  }

  @Test
  public void testSynonym2() throws IOException {
    NameUsageWrapper nuwIn = TestEntityGenerator.newNameUsageSynonymWrapper();
    String json = PAYLOAD_WRITER.writeValueAsString(nuwIn);
    LOG.debug(json);
    NameUsageWrapper nuwOut = PAYLOAD_READER.readValue(json);
    assertEquals(nuwIn, nuwOut);
  }

  @Test
  public void testBareName1() throws IOException {
    NameUsageWrapper nuwIn = TestEntityGenerator.newNameUsageBareNameWrapper();
    String json = PAYLOAD_WRITER.writeValueAsString(nuwIn);
    LOG.debug(json);
    NameUsageWrapper nuwOut = PAYLOAD_READER.readValue(json);
    assertEquals(nuwIn, nuwOut);
  }

  @Test
  public void testBareName2() throws IOException {
    NameUsageWrapper nuwIn = TestEntityGenerator.newNameUsageBareNameWrapper();
    String json = PAYLOAD_WRITER.writeValueAsString(nuwIn);
    LOG.debug(json);
    NameUsageWrapper nuwOut = PAYLOAD_READER.readValue(json);
    assertEquals(nuwIn, nuwOut);
  }

  @Test
  public void testEsNameUsage1() throws IOException {
    NameUsageDocument enuIn = new NameUsageDocument();
    enuIn.setPayload(PAYLOAD_WRITER.writeValueAsString(TestEntityGenerator.newNameUsageTaxonWrapper()));
    enuIn.setAuthorship("John Smith");
    enuIn.setDatasetKey(472);
    enuIn.setNameFields(EnumSet.of(NameField.COMBINATION_EX_AUTHORS, NameField.UNINOMIAL));
    enuIn.setNameId("16");
    enuIn.setNameIndexId("afd56770af");
    enuIn.setPublishedInId("AMO333");
    enuIn.setRank(Rank.SPECIES);
    enuIn.setStatus(TaxonomicStatus.ACCEPTED);
    enuIn.setType(NameType.SCIENTIFIC);
    enuIn.setVernacularNames(Arrays.asList("Apple tree"));

    String json = EsModule.writerFor(NameUsageDocument.class).withDefaultPrettyPrinter().writeValueAsString(enuIn);
    NameUsageDocument enuOut = EsModule.readerFor(NameUsageDocument.class).readValue(json);
    assertEquals(enuIn, enuOut);

    NameUsageWrapper nuw = PAYLOAD_READER.readValue(enuOut.getPayload()); 
    assertEquals(TestEntityGenerator.newNameUsageTaxonWrapper(), nuw);
    
  }

  @Test
  public void testEsNameUsage2() throws IOException {
    NameUsageDocument enuIn = new NameUsageDocument();
    enuIn.setPayload(
        PAYLOAD_WRITER.writeValueAsString(TestEntityGenerator.newNameUsageTaxonWrapper()));
    enuIn.setAuthorship("John Smith");
    enuIn.setDatasetKey(472);
    enuIn.setNameFields(EnumSet.of(NameField.COMBINATION_EX_AUTHORS, NameField.UNINOMIAL));
    enuIn.setNameId("16");
    enuIn.setNameIndexId("afd56770af");
    enuIn.setPublishedInId("AMO333");
    enuIn.setRank(Rank.SPECIES);
     enuIn.setStatus(TaxonomicStatus.ACCEPTED);
    enuIn.setType(NameType.SCIENTIFIC);
    enuIn.setVernacularNames(Arrays.asList("Apple tree"));

    String json = EsModule.writerFor(NameUsageDocument.class).writeValueAsString(enuIn);
    LOG.debug(json);

    NameUsageDocument enuOut = EsModule.readerFor(NameUsageDocument.class).readValue(json);
    assertEquals(enuIn, enuOut);

    NameUsageWrapper nuw = PAYLOAD_READER.readValue(enuOut.getPayload());
    assertEquals(TestEntityGenerator.newNameUsageTaxonWrapper(), nuw);
  }
  
}