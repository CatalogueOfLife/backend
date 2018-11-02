package org.col.es;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.col.api.TestEntityGenerator;
import org.col.api.search.NameUsageWrapper;
import org.col.api.vocab.NameField;
import org.col.api.vocab.TaxonomicStatus;
import org.col.es.model.EsNameUsage;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;

/*
 * Also separately tests serde for payload field, which contains the serialized NameUsageWrapper
 * object. NB Can't extend SerdeTestBase b/c it's specifically about (de)serialization to ES
 * documents, which uses another ObjectMapper.
 */
@Ignore("https://github.com/Sp2000/colplus-backend/issues/188")
public class EsNameUsageSerde {

  static Logger LOG = LoggerFactory.getLogger(EsNameUsageSerde.class);

  static EsConfig config1;
  static EsConfig config2;

  static ObjectWriter esWriter;
  static ObjectReader esReader;
  static ObjectWriter payloadWriter;
  static ObjectReader payloadReader;

  @BeforeClass
  public static void init() {
    config1 = new EsConfig();
    IndexConfig ic = new IndexConfig();
    ic.modelClass = EsNameUsage.class.getName();
    ic.storeEnumAsInt = Boolean.TRUE;
    config1.nameUsage = ic;

    config2 = new EsConfig();
    ic = new IndexConfig();
    ic.modelClass = EsNameUsage.class.getName();
    ic.storeEnumAsInt = Boolean.FALSE;
    config2.nameUsage = ic;
  }

  @Test
  public void testTaxon1() throws IOException {
    payloadWriter = config1.nameUsage.getMapper().writerFor(EsUtil.NUW_TYPE_REF);
    payloadReader = config1.nameUsage.getMapper().readerFor(EsUtil.NUW_TYPE_REF);
    NameUsageWrapper<?> nuwIn = TestEntityGenerator.newNameUsageTaxonWrapper();
    String json = payloadWriter.writeValueAsString(nuwIn);
    LOG.debug(json);
    NameUsageWrapper<?> nuwOut = payloadReader.readValue(json);
    assertEquals(nuwIn, nuwOut);
  }

  @Test
  public void testTaxon2() throws IOException {
    payloadWriter = config2.nameUsage.getMapper().writerFor(EsUtil.NUW_TYPE_REF);
    payloadReader = config2.nameUsage.getMapper().readerFor(EsUtil.NUW_TYPE_REF);
    NameUsageWrapper<?> nuwIn = TestEntityGenerator.newNameUsageTaxonWrapper();
    String json = payloadWriter.writeValueAsString(nuwIn);
    LOG.debug(json);
    NameUsageWrapper<?> nuwOut = payloadReader.readValue(json);
    assertEquals(nuwIn, nuwOut);
  }

  @Test
  public void testSynonym1() throws IOException {
    payloadWriter = config1.nameUsage.getMapper().writerFor(EsUtil.NUW_TYPE_REF);
    payloadReader = config1.nameUsage.getMapper().readerFor(EsUtil.NUW_TYPE_REF);
    NameUsageWrapper<?> nuwIn = TestEntityGenerator.newNameUsageSynonymWrapper();
    String json = payloadWriter.writeValueAsString(nuwIn);
    LOG.debug(json);
    NameUsageWrapper<?> nuwOut = payloadReader.readValue(json);
    assertEquals(nuwIn, nuwOut);
  }

  @Test
  public void testSynonym2() throws IOException {
    payloadWriter = config2.nameUsage.getMapper().writerFor(EsUtil.NUW_TYPE_REF);
    payloadReader = config2.nameUsage.getMapper().readerFor(EsUtil.NUW_TYPE_REF);
    NameUsageWrapper<?> nuwIn = TestEntityGenerator.newNameUsageSynonymWrapper();
    String json = payloadWriter.writeValueAsString(nuwIn);
    LOG.debug(json);
    NameUsageWrapper<?> nuwOut = payloadReader.readValue(json);
    assertEquals(nuwIn, nuwOut);
  }

  @Test
  public void testBareName1() throws IOException {
    payloadWriter = config1.nameUsage.getMapper().writerFor(EsUtil.NUW_TYPE_REF);
    payloadReader = config1.nameUsage.getMapper().readerFor(EsUtil.NUW_TYPE_REF);
    NameUsageWrapper<?> nuwIn = TestEntityGenerator.newNameUsageBareNameWrapper();
    String json = payloadWriter.writeValueAsString(nuwIn);
    LOG.debug(json);
    NameUsageWrapper<?> nuwOut = payloadReader.readValue(json);
    assertEquals(nuwIn, nuwOut);
  }

  @Test
  public void testBareName2() throws IOException {
    payloadWriter = config2.nameUsage.getMapper().writerFor(EsUtil.NUW_TYPE_REF);
    payloadReader = config2.nameUsage.getMapper().readerFor(EsUtil.NUW_TYPE_REF);
    NameUsageWrapper<?> nuwIn = TestEntityGenerator.newNameUsageBareNameWrapper();
    String json = payloadWriter.writeValueAsString(nuwIn);
    LOG.debug(json);
    NameUsageWrapper<?> nuwOut = payloadReader.readValue(json);
    assertEquals(nuwIn, nuwOut);
  }

  @Test
  public void testEsNameUsage1() throws IOException {
    esWriter = config1.nameUsage.getObjectWriter();
    esReader = config1.nameUsage.getObjectReader();
    payloadWriter = config1.nameUsage.getMapper().writerFor(EsUtil.NUW_TYPE_REF);
    payloadReader = config1.nameUsage.getMapper().readerFor(EsUtil.NUW_TYPE_REF);

    EsNameUsage enuIn = new EsNameUsage();
    enuIn.setPayload(
        payloadWriter.writeValueAsString(TestEntityGenerator.newNameUsageTaxonWrapper()));
    enuIn.setAuthorship("John Smith");
    enuIn.setDatasetKey(472);
    enuIn.setNameFields(EnumSet.of(NameField.COMBINATION_EX_AUTHORS, NameField.UNINOMIAL));
    enuIn.setNameId("16");
    enuIn.setNameIndexId("afd56770af");
    enuIn.setPublishedInId("AMO333");
    enuIn.setRank(Rank.SPECIES);
    enuIn.setScientificName("Malus Sylvestris");
    enuIn.setStatus(TaxonomicStatus.ACCEPTED);
    enuIn.setType(NameType.SCIENTIFIC);
    enuIn.setVernacularNames(Arrays.asList("Apple tree"));

    String json = esWriter.writeValueAsString(enuIn);
    LOG.debug(json);

    EsNameUsage enuOut = esReader.readValue(json);
    assertEquals(enuIn, enuOut);

    NameUsageWrapper<?> nuw = payloadReader.readValue(enuOut.getPayload());
    assertEquals(TestEntityGenerator.newNameUsageTaxonWrapper(), nuw);
  }

  @Test
  public void testEsNameUsage2() throws IOException {
    esWriter = config2.nameUsage.getObjectWriter();
    esReader = config2.nameUsage.getObjectReader();
    payloadWriter = config2.nameUsage.getMapper().writerFor(EsUtil.NUW_TYPE_REF);
    payloadReader = config2.nameUsage.getMapper().readerFor(EsUtil.NUW_TYPE_REF);

    EsNameUsage enuIn = new EsNameUsage();
    enuIn.setPayload(
        payloadWriter.writeValueAsString(TestEntityGenerator.newNameUsageTaxonWrapper()));
    enuIn.setAuthorship("John Smith");
    enuIn.setDatasetKey(472);
    enuIn.setNameFields(EnumSet.of(NameField.COMBINATION_EX_AUTHORS, NameField.UNINOMIAL));
    enuIn.setNameId("16");
    enuIn.setNameIndexId("afd56770af");
    enuIn.setPublishedInId("AMO333");
    enuIn.setRank(Rank.SPECIES);
    enuIn.setScientificName("Malus Sylvestris");
    enuIn.setStatus(TaxonomicStatus.ACCEPTED);
    enuIn.setType(NameType.SCIENTIFIC);
    enuIn.setVernacularNames(Arrays.asList("Apple tree"));

    String json = esWriter.writeValueAsString(enuIn);
    LOG.debug(json);

    EsNameUsage enuOut = esReader.readValue(json);
    assertEquals(enuIn, enuOut);

    NameUsageWrapper<?> nuw = payloadReader.readValue(enuOut.getPayload());
    assertEquals(TestEntityGenerator.newNameUsageTaxonWrapper(), nuw);
  }
}
