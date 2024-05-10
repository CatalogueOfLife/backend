package life.catalogue.es.nu.search;

import life.catalogue.api.model.*;
import life.catalogue.api.model.EditorialDecision.Mode;
import life.catalogue.api.search.*;
import life.catalogue.api.vocab.NomStatus;
import life.catalogue.es.EsNameUsage;
import life.catalogue.es.EsReadTestBase;
import life.catalogue.es.nu.NameUsageWrapperConverter;

import org.gbif.nameparser.api.Authorship;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.toList;
import static life.catalogue.api.search.NameUsageSearchParameter.*;
import static life.catalogue.api.search.NameUsageSearchRequest.IS_NOT_NULL;
import static life.catalogue.api.search.NameUsageSearchRequest.IS_NULL;
import static org.junit.Assert.assertEquals;

/**
 * <p>
 * Meant to lightly test all name usage search parameters using a simple (non-exhaustive) test. More subtle aspects of search and suggest
 * are not meant to be tested here.
 * <p>
 * As always: when adding tests, be mindful that after indexing NameUsageWrapper instances, they will have changed! Therefore you must
 * rebuild your test data when making assertions.
 */
public class NameUsageSearchParameterTest extends EsReadTestBase {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchParameterTest.class);
  private static EnumSet<NameUsageSearchParameter> tested = EnumSet.noneOf(NameUsageSearchParameter.class);

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void testTaxonId1() {

    SimpleName t1 = new SimpleName("1", null, null);
    SimpleName t2 = new SimpleName("2", null, null);
    SimpleName t3 = new SimpleName("3", null, null);
    SimpleName t4 = new SimpleName("4", null, null);
    SimpleName t5 = new SimpleName("5", null, null);
    SimpleName t6 = new SimpleName("6", null, null);

    NameUsageWrapper nuw1 = minimalTaxon();
    nuw1.setClassification(Arrays.asList(t1, t2, t3));

    NameUsageWrapper nuw2 = minimalTaxon();
    nuw2.setClassification(Arrays.asList(t2, t3, t4));

    NameUsageWrapper nuw3 = minimalTaxon();
    nuw3.setClassification(Arrays.asList(t3, t4, t5));

    NameUsageWrapper nuw4 = minimalTaxon();
    nuw4.setClassification(Arrays.asList(t4, t5, t6));

    index(nuw1, nuw2, nuw3, nuw4);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(TAXON_ID, "3");

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw3);
    assertEquals(expected, search(query).getResult());

    query = new NameUsageSearchRequest();
    query.addFilter(TAXON_ID, "4");
    expected = Arrays.asList(nuw2, nuw3, nuw4);
    assertEquals(expected, search(query).getResult());

    countdown(TAXON_ID);
  }

  @Test
  public void testTaxonId2() {
    SimpleName t1 = new SimpleName("1", null, null);
    SimpleName t2 = new SimpleName("2", null, null);
    SimpleName t3 = new SimpleName("3", null, null);
    SimpleName t4 = new SimpleName("4", null, null);
    SimpleName t5 = new SimpleName("5", null, null);
    SimpleName t6 = new SimpleName("6", null, null);
    SimpleName t7 = new SimpleName("7", null, null);
    SimpleName t8 = new SimpleName("8", null, null);

    NameUsageWrapper nuw1 = minimalTaxon();
    nuw1.setClassification(Arrays.asList(t1, t2, t3));

    NameUsageWrapper nuw2 = minimalTaxon();
    nuw2.setClassification(Arrays.asList(t2, t3, t4));

    NameUsageWrapper nuw3 = minimalTaxon();
    nuw3.setClassification(Arrays.asList(t3, t4, t5));

    NameUsageWrapper nuw4 = minimalTaxon();
    nuw4.setClassification(Arrays.asList(t4, t5, t6));

    NameUsageWrapper nuw5 = minimalTaxon();
    nuw5.setClassification(Arrays.asList(t5, t6, t7));

    NameUsageWrapper nuw6 = minimalTaxon();
    nuw6.setClassification(Arrays.asList(t6, t7, t8));


    index(nuw1, nuw2, nuw3, nuw4);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(TAXON_ID, "4");
    query.addFilter(TAXON_ID, "5");

    List<NameUsageWrapper> expected = Arrays.asList(nuw2, nuw3, nuw4);
    assertEquals(expected, search(query).getResult());

    countdown(TAXON_ID);
  }

  @Test
  public void testPublisherKey1() {
    UUID uuid1 = UUID.randomUUID();
    UUID uuid2 = UUID.randomUUID();
    NameUsageWrapper nuw1 = minimalTaxon();

    nuw1.setPublisherKey(uuid1);
    NameUsageWrapper nuw2 = minimalTaxon();

    nuw2.setPublisherKey(uuid1);
    NameUsageWrapper nuw3 = minimalTaxon();

    nuw3.setPublisherKey(uuid2);
    NameUsageWrapper nuw4 = minimalTaxon();
    nuw4.setPublisherKey(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
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
    NameUsageWrapper nuw1 = minimalTaxon();

    nuw1.setPublisherKey(uuid1);
    NameUsageWrapper nuw2 = minimalTaxon();

    nuw2.setPublisherKey(uuid1);
    NameUsageWrapper nuw3 = minimalTaxon();

    nuw3.setPublisherKey(uuid2);
    NameUsageWrapper nuw4 = minimalTaxon();

    nuw4.setPublisherKey(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(PUBLISHER_KEY, IS_NULL);

    List<NameUsageWrapper> expected = Arrays.asList(nuw4);
    assertEquals(expected, search(query).getResult());

    countdown(PUBLISHER_KEY);
  }

  @Test
  public void testPublisherKey3() {
    UUID uuid1 = UUID.randomUUID();
    UUID uuid2 = UUID.randomUUID();
    NameUsageWrapper nuw1 = minimalTaxon();

    nuw1.setPublisherKey(uuid1);
    NameUsageWrapper nuw2 = minimalTaxon();

    nuw2.setPublisherKey(uuid1);
    NameUsageWrapper nuw3 = minimalTaxon();

    nuw3.setPublisherKey(uuid2);
    NameUsageWrapper nuw4 = minimalTaxon();
    nuw4.setPublisherKey(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(PUBLISHER_KEY, IS_NULL);

    List<NameUsageWrapper> expected = Arrays.asList(nuw4);
    assertEquals(expected, search(query).getResult());

    countdown(PUBLISHER_KEY);
  }

  @Test
  public void testPublisherKey4() {
    UUID uuid1 = UUID.randomUUID();
    UUID uuid2 = UUID.randomUUID();
    NameUsageWrapper nuw1 = minimalTaxon();

    nuw1.setPublisherKey(uuid1);
    NameUsageWrapper nuw2 = minimalTaxon();

    nuw2.setPublisherKey(uuid1);
    NameUsageWrapper nuw3 = minimalTaxon();

    nuw3.setPublisherKey(uuid2);
    NameUsageWrapper nuw4 = minimalTaxon();
    nuw4.setPublisherKey(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(PUBLISHER_KEY, IS_NOT_NULL);

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw3);
    assertEquals(expected, search(query).getResult());

    countdown(PUBLISHER_KEY);
  }

  @Test
  public void testSectorPublisherKey() {
    UUID uuid1 = UUID.randomUUID();
    UUID uuid2 = UUID.randomUUID();

    NameUsageWrapper nuw1 = minimalTaxon();
    nuw1.setSectorPublisherKey(uuid1);

    NameUsageWrapper nuw2 = minimalTaxon();
    nuw2.setSectorPublisherKey(uuid1);

    NameUsageWrapper nuw3 = minimalTaxon();
    nuw3.setSectorPublisherKey(uuid2);

    NameUsageWrapper nuw4 = minimalTaxon();
    nuw4.setSectorPublisherKey(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(SECTOR_PUBLISHER_KEY, IS_NOT_NULL);

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw3);
    assertEquals(expected, search(query).getResult());

    countdown(SECTOR_PUBLISHER_KEY);
  }

  @Test
  public void testSectorMode() {
    NameUsageWrapper nuw1 = minimalTaxon();
    nuw1.getUsage().setSectorMode(Sector.Mode.ATTACH);

    NameUsageWrapper nuw2 = minimalTaxon();
    nuw2.getUsage().setSectorMode(Sector.Mode.ATTACH);

    NameUsageWrapper nuw3 = minimalTaxon();
    nuw3.getUsage().setSectorMode(Sector.Mode.MERGE);

    NameUsageWrapper nuw4 = minimalTaxon();
    nuw4.getUsage().setSectorMode(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(SECTOR_MODE, IS_NOT_NULL);

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw3);
    assertEquals(expected, search(query).getResult());

    query = new NameUsageSearchRequest();
    query.addFilter(SECTOR_MODE, Sector.Mode.MERGE);

    expected = Arrays.asList(nuw3);
    assertEquals(expected, search(query).getResult());

    countdown(SECTOR_MODE);
  }

  @Test
  public void testCatalogueKey() {
    /*
     * This apparently simple test really is a gotcha upon a gotcha debug nightmare with the production code winning and the test code
     * having to adapt. Catalog key alone (without decision mode) is ignored, so all documents should come back. However, the catalog key is
     * still used to prune the list of decisions within those documents, so the query result still differs from the original test data.
     */
    index(decisionTestData());
    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(CATALOGUE_KEY, 101);
    List<NameUsageWrapper> testData = decisionTestData();
    for (NameUsageWrapper nuw : testData) {
      if (nuw.getDecisions() != null) {
        nuw.setDecisions(nuw.getDecisions().stream().filter(sd -> sd.getDatasetKey() == 101).collect(toList()));
        if (nuw.getDecisions().isEmpty()) {
          nuw.setDecisions(null);
        }
      }
    }
    assertEquals(testData, search(query).getResult());
    countdown(CATALOGUE_KEY);
  }

  @Test
  public void testDecisionMode() {
    /*
     * This apparently simple test really is a gotcha upon a gotcha debug nightmare with the production code winning and the test code
     * having to adapt. Catalog key alone (without decision mode) is ignored, so all documents should come back. However, the catalog key is
     * still used to prune the list of decisions within those documents, so the query result still differs from the original test data.
     */
    index(decisionTestData());
    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(DECISION_MODE, Mode.REVIEWED);
    query.addFilter(CATALOGUE_KEY, 100);
    assertSearch(query, 100, 1, 2, decisionTestData());

    query = new NameUsageSearchRequest();
    query.addFilter(DECISION_MODE, IS_NULL);
    query.addFilter(CATALOGUE_KEY, 100);
    assertSearch(query, 100, 2, 5, decisionTestData());

    query = new NameUsageSearchRequest();
    query.addFilter(DECISION_MODE, IS_NOT_NULL);
    query.addFilter(CATALOGUE_KEY, 100);
    assertSearch(query, 100, 0, 2, decisionTestData());
    countdown(DECISION_MODE);
  }

  void assertSearch(NameUsageSearchRequest req, Integer catKey, int startIdx, int endIdx, List<NameUsageWrapper> nuws) {
    nuws.forEach(u -> {
      if (u.getDecisions() != null) {
        u.setDecisions(
          u.getDecisions().stream()
            .filter(d -> d.getDatasetKey().equals(catKey))
            .collect(Collectors.toUnmodifiableList())
        );
        if (u.getDecisions().isEmpty()) {
          u.setDecisions(null);
        }
      }
    });
    assertEquals(nuws.subList(startIdx, endIdx), search(req).getResult());
  }


  private List<NameUsageWrapper> decisionTestData() {
    NameUsageWrapper nuw1 = minimalTaxon();
    nuw1.setId("1");
    SimpleDecision sd = new SimpleDecision();
    sd.setDatasetKey(100);
    sd.setMode(Mode.BLOCK);
    // Must be mutable list (see above)
    nuw1.setDecisions(new ArrayList<>(List.of(sd)));

    NameUsageWrapper nuw2 = minimalTaxon();
    nuw2.setId("2");
    sd = new SimpleDecision();
    sd.setDatasetKey(100);
    sd.setMode(Mode.REVIEWED);
    nuw2.setDecisions(new ArrayList<>(List.of(sd)));

    NameUsageWrapper nuw3 = minimalTaxon();
    nuw3.setId("3");
    sd = new SimpleDecision();
    sd.setDatasetKey(101);
    sd.setMode(Mode.REVIEWED);
    nuw3.setDecisions(new ArrayList<>(List.of(sd)));

    NameUsageWrapper nuw4 = minimalTaxon();
    nuw4.setId("4");
    sd = new SimpleDecision();
    sd.setDatasetKey(101);
    sd.setMode(Mode.UPDATE_RECURSIVE);
    nuw4.setDecisions(new ArrayList<>(List.of(sd)));

    NameUsageWrapper nuw5 = minimalTaxon();
    nuw5.setId("5");
    nuw5.setDecisions(null);

    return List.of(nuw1, nuw2, nuw3, nuw4, nuw5);
  }

  @Test
  public void testAlphaIndex() {
    index(alphaIndexTestData());
    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(ALPHAINDEX, "a");
    assertEquals(alphaIndexTestData().subList(0, 1), search(query).getResult());

    query = new NameUsageSearchRequest();
    query.addFilter(ALPHAINDEX, "b");
    assertEquals(alphaIndexTestData().subList(1, 3), search(query).getResult());

    query = new NameUsageSearchRequest();
    query.addFilter(ALPHAINDEX, "z");
    assertEquals(Collections.emptyList(), search(query).getResult());
    countdown(ALPHAINDEX);
  }

  private List<NameUsageWrapper> alphaIndexTestData() {
    NameUsageWrapper nuw1 = minimalTaxon();
    nuw1.getUsage().getName().setScientificName("Alpha");
    NameUsageWrapper nuw2 = minimalTaxon();
    nuw2.getUsage().getName().setScientificName("Beta");
    NameUsageWrapper nuw3 = minimalTaxon();
    nuw3.getUsage().getName().setScientificName("Borneo");
    NameUsageWrapper nuw4 = minimalTaxon();
    nuw4.getUsage().getName().setScientificName("Crocodylidae");

    List<NameUsageWrapper> testData = List.of(nuw1, nuw2, nuw3, nuw4);
    return testData;
  }

  @Test
  public void testPublishedInId1() {
    String key1 = "100";
    String key2 = "101";
    NameUsageWrapper nuw1 = minimalTaxon();
    nuw1.getUsage().getName().setPublishedInId(key1);

    NameUsageWrapper nuw2 = minimalTaxon();
    nuw2.getUsage().getName().setPublishedInId(key1);

    NameUsageWrapper nuw3 = minimalTaxon();
    nuw3.getUsage().getName().setPublishedInId(key2);

    NameUsageWrapper nuw4 = minimalTaxon();
    nuw4.getUsage().getName().setPublishedInId(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(PUBLISHED_IN_ID, key2);

    List<NameUsageWrapper> expected = Arrays.asList(nuw3);
    assertEquals(expected, search(query).getResult());

    countdown(PUBLISHED_IN_ID);
  }

  @Test
  public void testPublishedInId2() {
    String key1 = "100";
    String key2 = "101";
    NameUsageWrapper nuw1 = minimalTaxon();
    nuw1.getUsage().getName().setPublishedInId(key1);

    NameUsageWrapper nuw2 = minimalTaxon();
    nuw2.getUsage().getName().setPublishedInId(key1);

    NameUsageWrapper nuw3 = minimalTaxon();
    nuw3.getUsage().getName().setPublishedInId(key2);

    NameUsageWrapper nuw4 = minimalTaxon();
    nuw4.getUsage().getName().setPublishedInId(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(PUBLISHED_IN_ID, key2);
    query.addFilter(PUBLISHED_IN_ID, IS_NULL);

    List<NameUsageWrapper> expected = Arrays.asList(nuw3, nuw4);
    assertEquals(expected, search(query).getResult());

    countdown(PUBLISHED_IN_ID);
  }

  @Test
  public void testPublishedInId3() {
    String key1 = "100";
    String key2 = "101";
    NameUsageWrapper nuw1 = minimalTaxon();
    nuw1.getUsage().getName().setPublishedInId(key1);

    NameUsageWrapper nuw2 = minimalTaxon();
    nuw2.getUsage().getName().setPublishedInId(key1);

    NameUsageWrapper nuw3 = minimalTaxon();
    nuw3.getUsage().getName().setPublishedInId(key2);

    NameUsageWrapper nuw4 = minimalTaxon();
    nuw4.getUsage().getName().setPublishedInId(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(PUBLISHED_IN_ID, IS_NOT_NULL);

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw3);
    assertEquals(expected, search(query).getResult());

    countdown(PUBLISHED_IN_ID);
  }

  @Test
  public void testNameId1() {
    String key1 = "100";
    String key2 = "101";
    NameUsageWrapper nuw1 = minimalTaxon();
    nuw1.getUsage().getName().setId(key1);

    NameUsageWrapper nuw2 = minimalTaxon();
    nuw2.getUsage().getName().setId(key1);

    NameUsageWrapper nuw3 = minimalTaxon();
    nuw3.getUsage().getName().setId(key2);

    NameUsageWrapper nuw4 = minimalTaxon();
    nuw4.getUsage().getName().setId(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(NAME_ID, key1);

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2);
    assertEquals(expected, search(query).getResult());

    countdown(NAME_ID);
  }

  @Test
  public void testDatasetKey1() {
    Integer key1 = 100;
    Integer key2 = 101;
    NameUsageWrapper nuw1 = minimalTaxon();
    nuw1.getUsage().getName().setDatasetKey(key1);

    NameUsageWrapper nuw2 = minimalTaxon();
    nuw2.getUsage().getName().setDatasetKey(key1);

    NameUsageWrapper nuw3 = minimalTaxon();
    nuw3.getUsage().getName().setDatasetKey(key2);

    NameUsageWrapper nuw4 = minimalTaxon();
    nuw4.getUsage().getName().setDatasetKey(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(DATASET_KEY, key1);

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2);
    assertEquals(expected, search(query).getResult());

    countdown(DATASET_KEY);
  }

  @Test
  public void testDatasetKey2() {
    Integer key1 = 100;
    Integer key2 = 101;
    NameUsageWrapper nuw1 = minimalTaxon();
    nuw1.getUsage().getName().setDatasetKey(key1);

    NameUsageWrapper nuw2 = minimalTaxon();
    nuw2.getUsage().getName().setDatasetKey(key1);

    NameUsageWrapper nuw3 = minimalTaxon();
    nuw3.getUsage().getName().setDatasetKey(key2);

    NameUsageWrapper nuw4 = minimalTaxon();
    nuw4.getUsage().getName().setDatasetKey(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(DATASET_KEY, 345678);

    List<NameUsageWrapper> expected = Collections.emptyList();
    assertEquals(expected, search(query).getResult());

    countdown(DATASET_KEY);
  }

  @Test
  public void testNomStatus1() {
    NameUsageWrapper nuw1 = minimalTaxon();
    nuw1.getUsage().getName().setNomStatus(NomStatus.CHRESONYM);

    NameUsageWrapper nuw2 = minimalTaxon();
    nuw2.getUsage().getName().setNomStatus(NomStatus.REJECTED);

    NameUsageWrapper nuw3 = minimalTaxon();
    nuw3.getUsage().getName().setNomStatus(NomStatus.NOT_ESTABLISHED);

    NameUsageWrapper nuw4 = minimalTaxon();
    nuw4.getUsage().getName().setNomStatus(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(NOM_STATUS, "not established");

    List<NameUsageWrapper> expected = Arrays.asList(nuw3);
    assertEquals(expected, search(query).getResult());

    countdown(NOM_STATUS);
  }

  @Test
  public void testNomStatus2() {
    NameUsageWrapper nuw1 = minimalTaxon();
    nuw1.getUsage().getName().setNomStatus(NomStatus.CHRESONYM);

    NameUsageWrapper nuw2 = minimalTaxon();
    nuw2.getUsage().getName().setNomStatus(NomStatus.REJECTED);

    NameUsageWrapper nuw3 = minimalTaxon();
    nuw3.getUsage().getName().setNomStatus(NomStatus.NOT_ESTABLISHED);

    NameUsageWrapper nuw4 = minimalTaxon();
    nuw4.getUsage().getName().setNomStatus(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(NOM_STATUS, NomStatus.CHRESONYM);
    query.addFilter(NOM_STATUS, NomStatus.REJECTED);
    query.addFilter(NOM_STATUS, IS_NULL);

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw4);
    assertEquals(expected, search(query).getResult());

    countdown(NOM_STATUS);
  }

  @Test
  public void testSectorKey1() {
    Integer key1 = 100;
    Integer key2 = 101;
    NameUsageWrapper nuw1 = minimalTaxon();
    ((Taxon) nuw1.getUsage()).setSectorKey(key1);

    NameUsageWrapper nuw2 = minimalTaxon();
    ((Taxon) nuw2.getUsage()).setSectorKey(key1);

    NameUsageWrapper nuw3 = minimalTaxon();
    ((Taxon) nuw3.getUsage()).setSectorKey(key2);

    NameUsageWrapper nuw4 = minimalTaxon();
    ((Taxon) nuw4.getUsage()).setSectorKey(null);

    index(nuw1, nuw2, nuw3, nuw4);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(SECTOR_KEY, IS_NOT_NULL);

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw3);
    assertEquals(expected, search(query).getResult());

    countdown(SECTOR_KEY);
  }

  @Test
  public void testAuthorShip() throws IOException {

    Authorship a1 = new Authorship();
    a1.setAuthors(List.of("Mark", "John"));
    a1.setExAuthors(List.of("Cornelius"));
    a1.setYear("2000");
    Authorship a2 = new Authorship();
    a2.setAuthors(List.of("Jim"));
    a2.setYear(null);
    Authorship a3 = new Authorship();
    a3.setAuthors(List.of("Cornelius"));
    a3.setExAuthors(List.of("Aaron", "Billy"));
    a3.setYear("1752");

    Name n = new Name();
    n.setBasionymAuthorship(a1);
    n.setCombinationAuthorship(a2);
    NameUsageWrapper nuw1 = minimalTaxon();
    nuw1.setUsage(new BareName(n));

    n = new Name();
    n.setBasionymAuthorship(a3);
    NameUsageWrapper nuw2 = minimalTaxon();
    nuw2.setUsage(new BareName(n));

    // Let's also check that the conversion from NameUsageWrapperConverter to document is as expected
    EsNameUsage doc = NameUsageWrapperConverter.toDocument(nuw1);
    assertEquals(List.of("Cornelius", "Jim", "John", "Mark"), new ArrayList<>(doc.getAuthorship()));
    assertEquals(List.of("2000"), new ArrayList<>(doc.getAuthorshipYear()));

    doc = NameUsageWrapperConverter.toDocument(nuw2);
    assertEquals(List.of("Aaron", "Billy", "Cornelius"), new ArrayList<>(doc.getAuthorship()));
    assertEquals(List.of("1752"), new ArrayList<>(doc.getAuthorshipYear()));

    // Index the name usages
    index(nuw1, nuw2);

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFacet(AUTHORSHIP);
    query.addFacet(AUTHORSHIP_YEAR);

    NameUsageSearchResponse result = search(query);

    Set<FacetValue<?>> facets = result.getFacets().get(AUTHORSHIP);

    LinkedHashSet<FacetValue<?>> expected = new LinkedHashSet<>(List.of(
        FacetValue.forString("Cornelius", 2),
        FacetValue.forString("Aaron", 1),
        FacetValue.forString("Billy", 1),
        FacetValue.forString("Jim", 1),
        FacetValue.forString("John", 1),
        FacetValue.forString("Mark", 1)));

    assertEquals(expected, facets);

    facets = result.getFacets().get(AUTHORSHIP_YEAR);

    expected = new LinkedHashSet<>(List.of(
        FacetValue.forString("1752", 1),
        FacetValue.forString("2000", 1)));

    assertEquals(expected, facets);

    countdown(AUTHORSHIP);
    countdown(AUTHORSHIP_YEAR);
  }

  private static void countdown(NameUsageSearchParameter param) {
    tested.add(param);
    LOG.info("-->  Name search parameter {} tested", param);
    if (tested.size() == NameUsageSearchParameter.values().length) {
      LOG.info("***  All name search parameters tested!");
    } else {
      LOG.info("*** {} parameter(s) tested. {} more to go", tested.size(),
          NameUsageSearchParameter.values().length - tested.size());
      Set<NameUsageSearchParameter> todo = EnumSet.allOf(NameUsageSearchParameter.class);
      todo.removeAll(tested);
      String todoStr = todo.stream().map(NameUsageSearchParameter::toString).collect(Collectors.joining(", "));
      LOG.info("*** Missing: {}", todoStr);
    }
  }

}
