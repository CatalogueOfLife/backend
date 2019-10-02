package org.col.es.name.search;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.col.api.model.Name;
import org.col.api.model.NameUsage;
import org.col.api.model.SimpleName;
import org.col.api.model.Taxon;
import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameUsageWrapper;
import org.col.api.vocab.NomStatus;
import org.col.es.EsReadTestBase;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.api.search.NameSearchParameter.DATASET_KEY;
import static org.col.api.search.NameSearchParameter.DECISION_KEY;
import static org.col.api.search.NameSearchParameter.NAME_ID;
import static org.col.api.search.NameSearchParameter.NAME_INDEX_ID;
import static org.col.api.search.NameSearchParameter.NOM_STATUS;
import static org.col.api.search.NameSearchParameter.PUBLISHED_IN_ID;
import static org.col.api.search.NameSearchParameter.PUBLISHER_KEY;
import static org.col.api.search.NameSearchParameter.SECTOR_KEY;
import static org.col.api.search.NameSearchParameter.TAXON_ID;
import static org.col.api.search.NameSearchRequest.IS_NOT_NULL;
import static org.col.api.search.NameSearchRequest.IS_NULL;
import static org.junit.Assert.assertEquals;

public class NameSearchTestAllParamsTest extends EsReadTestBase {

  private static final Logger LOG = LoggerFactory.getLogger(NameSearchTestAllParamsTest.class);
  private static EnumSet<NameSearchParameter> tested = EnumSet.noneOf(NameSearchParameter.class);

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  /*
   * Create a minimalistic NameUsageWrapper - just enough to allow it to be indexed without NPEs & stuff.
   */
  private static NameUsageWrapper minimalNameUsage() {
    NameUsage bogus = new Taxon();
    bogus.setName(new Name());
    NameUsageWrapper nuw = new NameUsageWrapper();
    nuw.setUsage(bogus);
    return nuw;
  }

  @Test
  public void testTaxonID1() {

    SimpleName t1 = new SimpleName("1", null, null);
    SimpleName t2 = new SimpleName("2", null, null);
    SimpleName t3 = new SimpleName("3", null, null);
    SimpleName t4 = new SimpleName("4", null, null);
    SimpleName t5 = new SimpleName("5", null, null);
    SimpleName t6 = new SimpleName("6", null, null);

    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.setClassification(Arrays.asList(t1, t2, t3));
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.setClassification(Arrays.asList(t2, t3, t4));
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.setClassification(Arrays.asList(t3, t4, t5));
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.setClassification(Arrays.asList(t4, t5, t6));

    index(nuw1, nuw2, nuw3, nuw4);

    NameSearchRequest query = new NameSearchRequest();
    query.addFilter(TAXON_ID, "3");

    /*
     * Yikes - again, remember to resurrect the NameUsageWrappers b/c they will get pruned on insert !!!
     */
    nuw1.setClassification(Arrays.asList(t1, t2, t3));
    nuw2.setClassification(Arrays.asList(t2, t3, t4));
    nuw3.setClassification(Arrays.asList(t3, t4, t5));
    nuw4.setClassification(Arrays.asList(t4, t5, t6));
    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw3);

    assertEquals(expected, search(query).getResult());

    countdown(TAXON_ID);

  }

  @Test
  public void testTaxonID2() {
    SimpleName t1 = new SimpleName("1", null, null);
    SimpleName t2 = new SimpleName("2", null, null);
    SimpleName t3 = new SimpleName("3", null, null);
    SimpleName t4 = new SimpleName("4", null, null);
    SimpleName t5 = new SimpleName("5", null, null);
    SimpleName t6 = new SimpleName("6", null, null);
    SimpleName t7 = new SimpleName("7", null, null);
    SimpleName t8 = new SimpleName("8", null, null);

    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.setClassification(Arrays.asList(t1, t2, t3));
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.setClassification(Arrays.asList(t2, t3, t4));
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.setClassification(Arrays.asList(t3, t4, t5));
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.setClassification(Arrays.asList(t4, t5, t6));
    NameUsageWrapper nuw5 = minimalNameUsage();
    nuw5.setClassification(Arrays.asList(t5, t6, t7));
    NameUsageWrapper nuw6 = minimalNameUsage();
    nuw6.setClassification(Arrays.asList(t6, t7, t8));

    index(nuw1, nuw2, nuw3, nuw4);

    NameSearchRequest query = new NameSearchRequest();
    query.addFilter(TAXON_ID, "4");
    query.addFilter(TAXON_ID, "5");

    /*
     * Yikes - again, remember to resurrect the NameUsageWrappers b/c they will get pruned on insert !!!
     */
    nuw1.setClassification(Arrays.asList(t1, t2, t3));
    nuw2.setClassification(Arrays.asList(t2, t3, t4));
    nuw3.setClassification(Arrays.asList(t3, t4, t5));
    nuw4.setClassification(Arrays.asList(t4, t5, t6));
    nuw5.setClassification(Arrays.asList(t5, t6, t7));
    nuw6.setClassification(Arrays.asList(t6, t7, t8));

    List<NameUsageWrapper> expected = Arrays.asList(nuw2, nuw3, nuw4);

    assertEquals(expected, search(query).getResult());

    countdown(TAXON_ID);

  }

  @Test
  public void testPublisherKey1() {
    UUID uuid1 = UUID.randomUUID();
    UUID uuid2 = UUID.randomUUID();
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.setPublisherKey(uuid1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.setPublisherKey(uuid1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.setPublisherKey(uuid2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.setPublisherKey(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameSearchRequest query = new NameSearchRequest();
    query.addFilter(PUBLISHER_KEY, uuid1);

    /*
     * Yikes - again, remember to resurrect the expected result, because NameUsageWrappers will get pruned on insert !!!
     */
    nuw1.setPublisherKey(uuid1);
    nuw2.setPublisherKey(uuid1);
    nuw3.setPublisherKey(uuid2);
    nuw4.setPublisherKey(null);

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2);

    assertEquals(expected, search(query).getResult());

    countdown(PUBLISHER_KEY);

  }

  @Test
  public void testPublisherKey2() {
    UUID uuid1 = UUID.randomUUID();
    UUID uuid2 = UUID.randomUUID();
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.setPublisherKey(uuid1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.setPublisherKey(uuid1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.setPublisherKey(uuid2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.setPublisherKey(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameSearchRequest query = new NameSearchRequest();
    query.addFilter(PUBLISHER_KEY, IS_NULL);

    nuw1.setPublisherKey(uuid1);
    nuw2.setPublisherKey(uuid1);
    nuw3.setPublisherKey(uuid2);
    nuw4.setPublisherKey(null);

    List<NameUsageWrapper> expected = Arrays.asList(nuw4);

    assertEquals(expected, search(query).getResult());

    countdown(PUBLISHER_KEY);

  }

  @Test
  public void testPublisherKey3() {
    UUID uuid1 = UUID.randomUUID();
    UUID uuid2 = UUID.randomUUID();
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.setPublisherKey(uuid1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.setPublisherKey(uuid1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.setPublisherKey(uuid2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.setPublisherKey(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameSearchRequest query = new NameSearchRequest();
    query.addFilter(PUBLISHER_KEY, IS_NULL);

    nuw1.setPublisherKey(uuid1);
    nuw2.setPublisherKey(uuid1);
    nuw3.setPublisherKey(uuid2);
    nuw4.setPublisherKey(null);

    List<NameUsageWrapper> expected = Arrays.asList(nuw4);

    assertEquals(expected, search(query).getResult());

    countdown(PUBLISHER_KEY);

  }

  @Test
  public void testPublisherKey4() {
    UUID uuid1 = UUID.randomUUID();
    UUID uuid2 = UUID.randomUUID();
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.setPublisherKey(uuid1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.setPublisherKey(uuid1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.setPublisherKey(uuid2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.setPublisherKey(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameSearchRequest query = new NameSearchRequest();
    query.addFilter(PUBLISHER_KEY, IS_NOT_NULL);

    nuw1.setPublisherKey(uuid1);
    nuw2.setPublisherKey(uuid1);
    nuw3.setPublisherKey(uuid2);
    nuw4.setPublisherKey(null);

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw3);

    assertEquals(expected, search(query).getResult());

    countdown(PUBLISHER_KEY);

  }

  @Test
  public void testDecisionKey1() {
    Integer key1 = 100;
    Integer key2 = 101;
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.setDecisionKey(key1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.setDecisionKey(key1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.setDecisionKey(key2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.setDecisionKey(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameSearchRequest query = new NameSearchRequest();
    query.addFilter(DECISION_KEY, key1);

    nuw1.setDecisionKey(key1);
    nuw2.setDecisionKey(key1);
    nuw3.setDecisionKey(key2);
    nuw4.setDecisionKey(null);
    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2);

    assertEquals(expected, search(query).getResult());

    countdown(DECISION_KEY);

  }

  @Test
  public void testDecisionKey2() {
    Integer key1 = 100;
    Integer key2 = 101;
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.setDecisionKey(key1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.setDecisionKey(key1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.setDecisionKey(key2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.setDecisionKey(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameSearchRequest query = new NameSearchRequest();
    query.addFilter(DECISION_KEY, IS_NOT_NULL);

    nuw1.setDecisionKey(key1);
    nuw2.setDecisionKey(key1);
    nuw3.setDecisionKey(key2);
    nuw4.setDecisionKey(null);
    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw3);

    assertEquals(expected, search(query).getResult());

    countdown(DECISION_KEY);

  }

  @Test
  public void testDecisionKey3() {
    Integer key1 = 100;
    Integer key2 = 101;
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.setDecisionKey(key1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.setDecisionKey(key1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.setDecisionKey(key2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.setDecisionKey(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameSearchRequest query = new NameSearchRequest();
    query.addFilter(DECISION_KEY, IS_NULL);

    nuw1.setDecisionKey(key1);
    nuw2.setDecisionKey(key1);
    nuw3.setDecisionKey(key2);
    nuw4.setDecisionKey(null);
    List<NameUsageWrapper> expected = Arrays.asList(nuw4);

    assertEquals(expected, search(query).getResult());

    countdown(DECISION_KEY);

  }

  @Test
  public void testNameIndexId1() {
    String key1 = "100";
    String key2 = "101";
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.getUsage().getName().setNameIndexId(key1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.getUsage().getName().setNameIndexId(key1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.getUsage().getName().setNameIndexId(key2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.getUsage().getName().setNameIndexId(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameSearchRequest query = new NameSearchRequest();
    query.addFilter(NAME_INDEX_ID, key2);

    nuw1.getUsage().getName().setNameIndexId(key1);
    nuw2.getUsage().getName().setNameIndexId(key1);
    nuw3.getUsage().getName().setNameIndexId(key2);
    nuw4.getUsage().getName().setNameIndexId(null);
    List<NameUsageWrapper> expected = Arrays.asList(nuw3);

    assertEquals(expected, search(query).getResult());

    countdown(NAME_INDEX_ID);
  }

  @Test
  public void testNameIndexId2() {
    String key1 = "100";
    String key2 = "101";
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.getUsage().getName().setNameIndexId(key1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.getUsage().getName().setNameIndexId(key1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.getUsage().getName().setNameIndexId(key2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.getUsage().getName().setNameIndexId(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameSearchRequest query = new NameSearchRequest();
    query.addFilter(NAME_INDEX_ID, IS_NULL);

    nuw1.getUsage().getName().setNameIndexId(key1);
    nuw2.getUsage().getName().setNameIndexId(key1);
    nuw3.getUsage().getName().setNameIndexId(key2);
    nuw4.getUsage().getName().setNameIndexId(null);
    List<NameUsageWrapper> expected = Arrays.asList(nuw4);

    assertEquals(expected, search(query).getResult());

    countdown(NAME_INDEX_ID);

  }

  @Test
  public void testNameIndexId3() {
    String key1 = "100";
    String key2 = "101";
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.getUsage().getName().setNameIndexId(key1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.getUsage().getName().setNameIndexId(key1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.getUsage().getName().setNameIndexId(key2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.getUsage().getName().setNameIndexId(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameSearchRequest query = new NameSearchRequest();
    query.addFilter(NAME_INDEX_ID, IS_NOT_NULL);

    nuw1.getUsage().getName().setNameIndexId(key1);
    nuw2.getUsage().getName().setNameIndexId(key1);
    nuw3.getUsage().getName().setNameIndexId(key2);
    nuw4.getUsage().getName().setNameIndexId(null);
    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw3);

    assertEquals(expected, search(query).getResult());

    countdown(NAME_INDEX_ID);

  }

  @Test
  public void testPublishedInId1() {
    String key1 = "100";
    String key2 = "101";
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.getUsage().getName().setPublishedInId(key1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.getUsage().getName().setPublishedInId(key1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.getUsage().getName().setPublishedInId(key2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.getUsage().getName().setPublishedInId(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameSearchRequest query = new NameSearchRequest();
    query.addFilter(PUBLISHED_IN_ID, key2);

    nuw1.getUsage().getName().setPublishedInId(key1);
    nuw2.getUsage().getName().setPublishedInId(key1);
    nuw3.getUsage().getName().setPublishedInId(key2);
    nuw4.getUsage().getName().setNameIndexId(null);
    List<NameUsageWrapper> expected = Arrays.asList(nuw3);

    assertEquals(expected, search(query).getResult());

    countdown(PUBLISHED_IN_ID);
  }

  @Test
  public void testPublishedInId2() {
    String key1 = "100";
    String key2 = "101";
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.getUsage().getName().setPublishedInId(key1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.getUsage().getName().setPublishedInId(key1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.getUsage().getName().setPublishedInId(key2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.getUsage().getName().setPublishedInId(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameSearchRequest query = new NameSearchRequest();
    query.addFilter(PUBLISHED_IN_ID, key2);
    query.addFilter(PUBLISHED_IN_ID, IS_NULL);

    nuw1.getUsage().getName().setPublishedInId(key1);
    nuw2.getUsage().getName().setPublishedInId(key1);
    nuw3.getUsage().getName().setPublishedInId(key2);
    nuw4.getUsage().getName().setNameIndexId(null);
    List<NameUsageWrapper> expected = Arrays.asList(nuw3, nuw4);

    assertEquals(expected, search(query).getResult());

    countdown(PUBLISHED_IN_ID);
  }

  @Test
  public void testPublishedInId3() {
    String key1 = "100";
    String key2 = "101";
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.getUsage().getName().setPublishedInId(key1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.getUsage().getName().setPublishedInId(key1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.getUsage().getName().setPublishedInId(key2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.getUsage().getName().setPublishedInId(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameSearchRequest query = new NameSearchRequest();
    query.addFilter(PUBLISHED_IN_ID, IS_NOT_NULL);

    nuw1.getUsage().getName().setPublishedInId(key1);
    nuw2.getUsage().getName().setPublishedInId(key1);
    nuw3.getUsage().getName().setPublishedInId(key2);
    nuw4.getUsage().getName().setNameIndexId(null);
    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw3);

    assertEquals(expected, search(query).getResult());

    countdown(PUBLISHED_IN_ID);
  }

  @Test
  public void testNameId1() {
    String key1 = "100";
    String key2 = "101";
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.getUsage().getName().setId(key1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.getUsage().getName().setId(key1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.getUsage().getName().setId(key2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.getUsage().getName().setId(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameSearchRequest query = new NameSearchRequest();
    query.addFilter(NAME_ID, key1);

    nuw1.getUsage().getName().setId(key1);
    nuw2.getUsage().getName().setId(key1);
    nuw3.getUsage().getName().setId(key2);
    nuw4.getUsage().getName().setId(null);
    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2);

    assertEquals(expected, search(query).getResult());

    countdown(NAME_ID);
  }

  @Test
  public void testDatasetKey1() {
    Integer key1 = 100;
    Integer key2 = 101;
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.getUsage().getName().setDatasetKey(key1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.getUsage().getName().setDatasetKey(key1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.getUsage().getName().setDatasetKey(key2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.getUsage().getName().setDatasetKey(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameSearchRequest query = new NameSearchRequest();
    query.addFilter(DATASET_KEY, key1);

    nuw1.getUsage().getName().setDatasetKey(key1);
    nuw2.getUsage().getName().setDatasetKey(key1);
    nuw3.getUsage().getName().setDatasetKey(key2);
    nuw4.getUsage().getName().setDatasetKey(null);
    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2);

    assertEquals(expected, search(query).getResult());

    countdown(DATASET_KEY);
  }

  @Test
  public void testDatasetKey2() {
    Integer key1 = 100;
    Integer key2 = 101;
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.getUsage().getName().setDatasetKey(key1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.getUsage().getName().setDatasetKey(key1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.getUsage().getName().setDatasetKey(key2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.getUsage().getName().setDatasetKey(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameSearchRequest query = new NameSearchRequest();
    query.addFilter(DATASET_KEY, 345678);

    nuw1.getUsage().getName().setDatasetKey(key1);
    nuw2.getUsage().getName().setDatasetKey(key1);
    nuw3.getUsage().getName().setDatasetKey(key2);
    nuw4.getUsage().getName().setDatasetKey(null);
    List<NameUsageWrapper> expected = Collections.emptyList();

    assertEquals(expected, search(query).getResult());

    countdown(DATASET_KEY);
  }

  @Test
  public void testNomStatus1() {
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.getUsage().getName().setNomStatus(NomStatus.CHRESONYM);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.getUsage().getName().setNomStatus(NomStatus.REJECTED);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.getUsage().getName().setNomStatus(NomStatus.NOT_ESTABLISHED);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.getUsage().getName().setNomStatus(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameSearchRequest query = new NameSearchRequest();
    query.addFilter(NOM_STATUS, "not established");

    nuw1.getUsage().getName().setNomStatus(NomStatus.CHRESONYM);
    nuw2.getUsage().getName().setNomStatus(NomStatus.REJECTED);
    nuw3.getUsage().getName().setNomStatus(NomStatus.NOT_ESTABLISHED);
    nuw4.getUsage().getName().setNomStatus(null);
    List<NameUsageWrapper> expected = Arrays.asList(nuw3);

    assertEquals(expected, search(query).getResult());

    countdown(NOM_STATUS);
  }

  @Test
  public void testNomStatus2() {
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.getUsage().getName().setNomStatus(NomStatus.CHRESONYM);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.getUsage().getName().setNomStatus(NomStatus.REJECTED);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.getUsage().getName().setNomStatus(NomStatus.NOT_ESTABLISHED);
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.getUsage().getName().setNomStatus(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameSearchRequest query = new NameSearchRequest();
    query.addFilter(NOM_STATUS, NomStatus.CHRESONYM);
    query.addFilter(NOM_STATUS, NomStatus.REJECTED);
    query.addFilter(NOM_STATUS, IS_NULL);

    nuw1.getUsage().getName().setNomStatus(NomStatus.CHRESONYM);
    nuw2.getUsage().getName().setNomStatus(NomStatus.REJECTED);
    nuw3.getUsage().getName().setNomStatus(NomStatus.NOT_ESTABLISHED);
    nuw4.getUsage().getName().setNomStatus(null);
    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw4);

    assertEquals(expected, search(query).getResult());

    countdown(NOM_STATUS);
  }

  @Test
  public void testSectorKey1() {
    Integer key1 = 100;
    Integer key2 = 101;
    NameUsageWrapper nuw1 = minimalNameUsage();
    ((Taxon) nuw1.getUsage()).setSectorKey(key1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    ((Taxon) nuw2.getUsage()).setSectorKey(key1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    ((Taxon) nuw3.getUsage()).setSectorKey(key2);
    NameUsageWrapper nuw4 = minimalNameUsage();
    ((Taxon) nuw4.getUsage()).setSectorKey(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameSearchRequest query = new NameSearchRequest();
    query.addFilter(SECTOR_KEY, IS_NOT_NULL);

    ((Taxon) nuw1.getUsage()).setSectorKey(key1);
    ((Taxon) nuw2.getUsage()).setSectorKey(key1);
    ((Taxon) nuw3.getUsage()).setSectorKey(key2);
    ((Taxon) nuw4.getUsage()).setSectorKey(null);
    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw3);

    assertEquals(expected, search(query).getResult());

    countdown(SECTOR_KEY);

  }

  private static void countdown(NameSearchParameter param) {
    tested.add(param);
    LOG.info("####  Name search parameter {} unit tested  ####", param);
    if (tested.size() == NameSearchParameter.values().length) {
      LOG.info("####  All name search parameters tested!  ####");
    } else {
      LOG.info("####  {} parameter(s) tested. {} more to go  ####", tested.size(), NameSearchParameter.values().length - tested.size());
      Set<NameSearchParameter> todo = EnumSet.allOf(NameSearchParameter.class);
      todo.removeAll(tested);
      String todoStr = todo.stream().map(NameSearchParameter::toString).collect(Collectors.joining(", "));
      LOG.info("####  Missing: {}  ####", todoStr);
    }
  }

}
