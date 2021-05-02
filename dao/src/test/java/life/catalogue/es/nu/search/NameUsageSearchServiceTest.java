package life.catalogue.es.nu.search;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.search.*;
import life.catalogue.api.search.NameUsageRequest.SearchType;
import life.catalogue.api.vocab.Environment;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.es.EsReadTestBase;
import life.catalogue.es.nu.NameUsageWrapperConverter;
import org.elasticsearch.client.RestClient;
import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.*;

import static life.catalogue.es.EsUtil.insert;
import static life.catalogue.es.EsUtil.refreshIndex;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class NameUsageSearchServiceTest extends EsReadTestBase {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchServiceTest.class);

  private static RestClient client;
  private static NameUsageSearchServiceEs svc;
  private static NameUsageWrapperConverter CONVERTER = new NameUsageWrapperConverter();

  @BeforeClass
  public static void init() {
    client = esSetupRule.getClient();
    svc = new NameUsageSearchServiceEs(esSetupRule.getEsConfig().nameUsage.name, esSetupRule.getClient());
  }

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void testQuery1() throws IOException {
    NameUsageWrapperConverter converter = new NameUsageWrapperConverter();

    // Define search
    NameUsageSearchRequest nsr = new NameUsageSearchRequest();
    nsr.setHighlight(false);
    nsr.addFilter(NameUsageSearchParameter.ISSUE, Issue.ACCEPTED_NAME_MISSING);

    // Match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING));
    nuw1.getUsage().setOrigin(Origin.IMPLICIT_NAME);
    insert(client, indexName(), converter.toDocument(nuw1));

    // Match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.SCRUTINIZER_DATE_INVALID));
    insert(client, indexName(), converter.toDocument(nuw2));

    // Match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName(), converter.toDocument(nuw3));

    // No match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(null);
    insert(client, indexName(), converter.toDocument(nuw4));

    // No match
    NameUsageWrapper nuw5 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw5.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName(), converter.toDocument(nuw5));

    // No match
    NameUsageWrapper nuw6 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw6.setIssues(EnumSet.of(Issue.CITATION_UNPARSED, Issue.BASIONYM_ID_INVALID));
    insert(client, indexName(), converter.toDocument(nuw6));

    // No match
    NameUsageWrapper nuw7 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw7.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName(), converter.toDocument(nuw7));

    refreshIndex(client, indexName());

    ResultPage<NameUsageWrapper> result = svc.search(indexName(), nsr, new Page());
    assertEquals(3, result.getResult().size());

    // search for origin
    nsr.addFilter(NameUsageSearchParameter.ORIGIN, Origin.IMPLICIT_NAME);
    result = svc.search(indexName(), nsr, new Page());
    assertEquals(1, result.getResult().size());

    nsr.clearFilter(NameUsageSearchParameter.ORIGIN);
    nsr.addFilter(NameUsageSearchParameter.ORIGIN, Origin.BASIONYM_PLACEHOLDER);
    result = svc.search(indexName(), nsr, new Page());
    assertEquals(0, result.getResult().size());
  }

  @Test
  public void testQuery2() throws IOException {
    NameUsageWrapperConverter converter = new NameUsageWrapperConverter();

    // Find all documents with an issue of either ACCEPTED_NAME_MISSING or ACCORDING_TO_DATE_INVALID
    NameUsageSearchRequest nsr = new NameUsageSearchRequest();
    nsr.addFilter(NameUsageSearchParameter.ISSUE, EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.SCRUTINIZER_DATE_INVALID));

    // Match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING));
    insert(client, indexName(), converter.toDocument(nuw1));

    // Match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.SCRUTINIZER_DATE_INVALID));
    insert(client, indexName(), converter.toDocument(nuw2));

    // Match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName(), converter.toDocument(nuw3));

    // No match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(null);
    insert(client, indexName(), converter.toDocument(nuw4));

    // No match
    NameUsageWrapper nuw5 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw5.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName(), converter.toDocument(nuw5));

    // No match
    NameUsageWrapper nuw6 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw6.setIssues(EnumSet.of(Issue.CITATION_UNPARSED, Issue.BASIONYM_ID_INVALID));
    insert(client, indexName(), converter.toDocument(nuw6));

    // No match
    NameUsageWrapper nuw7 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw7.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName(), converter.toDocument(nuw7));

    // No match
    NameUsageWrapper nuw8 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw8.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.DOUBTFUL_NAME));
    insert(client, indexName(), converter.toDocument(nuw8));

    refreshIndex(client, indexName());

    ResultPage<NameUsageWrapper> result = svc.search(indexName(), nsr, new Page());

    assertEquals(4, result.getResult().size());
  }

  @Test
  public void testQuery3() throws IOException {
    NameUsageWrapperConverter converter = new NameUsageWrapperConverter();

    // Find all documents with an issue of any of ACCEPTED_NAME_MISSING, ACCORDING_TO_DATE_INVALID, BASIONYM_ID_INVALID
    NameUsageSearchRequest nsr = new NameUsageSearchRequest();
    nsr.setHighlight(false);
    nsr.addFilter(NameUsageSearchParameter.ISSUE,
        Issue.ACCEPTED_NAME_MISSING,
        Issue.SCRUTINIZER_DATE_INVALID,
        Issue.BASIONYM_ID_INVALID);

    // Match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING));
    insert(client, indexName(), converter.toDocument(nuw1));

    // Match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.SCRUTINIZER_DATE_INVALID));
    insert(client, indexName(), converter.toDocument(nuw2));

    // Match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName(), converter.toDocument(nuw3));

    // No match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(null);
    insert(client, indexName(), converter.toDocument(nuw4));

    // No match
    NameUsageWrapper nuw5 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw5.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName(), converter.toDocument(nuw5));

    // Match
    NameUsageWrapper nuw6 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw6.setIssues(EnumSet.of(Issue.CITATION_UNPARSED, Issue.BASIONYM_ID_INVALID));
    insert(client, indexName(), converter.toDocument(nuw6));

    // No match
    NameUsageWrapper nuw7 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw7.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName(), converter.toDocument(nuw7));

    refreshIndex(client, indexName());

    ResultPage<NameUsageWrapper> result = svc.search(indexName(), nsr, new Page());

    assertEquals(4, result.getResult().size());
  }


  @Test
  public void testIsNull() throws IOException {
    NameUsageWrapperConverter converter = new NameUsageWrapperConverter();

    // Define search condition
    NameUsageSearchRequest nsr = new NameUsageSearchRequest();
    nsr.setHighlight(false);
    nsr.addFilter(NameUsageSearchParameter.ISSUE, NameUsageRequest.IS_NULL);

    // Match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName(), converter.toDocument(nuw1));
    // No match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName(), converter.toDocument(nuw2));
    // Match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(null);
    insert(client, indexName(), converter.toDocument(nuw3));
    // No match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName(), converter.toDocument(nuw4));

    refreshIndex(client, indexName());

    ResultPage<NameUsageWrapper> result = svc.search(indexName(), nsr, new Page());

    assertEquals(2, result.getResult().size());
  }

  @Test
  public void testIsNotNull() throws IOException {
    NameUsageWrapperConverter converter = new NameUsageWrapperConverter();

    // Define search condition
    NameUsageSearchRequest nsr = new NameUsageSearchRequest();
    nsr.addFilter(NameUsageSearchParameter.ISSUE, NameUsageRequest.IS_NOT_NULL);

    // No match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName(), converter.toDocument(nuw1));
    // Match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName(), converter.toDocument(nuw2));
    // No match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(null);
    insert(client, indexName(), converter.toDocument(nuw3));
    // Match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName(), converter.toDocument(nuw4));

    refreshIndex(client, indexName());

    ResultPage<NameUsageWrapper> result = svc.search(indexName(), nsr, new Page());

    assertEquals(2, result.getResult().size());
  }

  /**
   * @return id of the new taxon
   */
  private String insertRndTaxon(int datasetKey) throws IOException {
    Taxon t = TestEntityGenerator.newTaxon();
    // taxon id will be pruned by indexing code
    final String id = t.getId();
    t.setDatasetKey(datasetKey);
    t.getName().setDatasetKey(datasetKey);
    NameUsageWrapper nuw = new NameUsageWrapper(t);
    insert(client, indexName(), CONVERTER.toDocument(nuw));
    return id;
  }

  /**
   * Tests for mulitple usage ID queries
   */
  @Test
  public void testUsageIDSearch() throws IOException {
    final int datasetKey = 1000;
    List<String> ids = new ArrayList<>();
    ids.add(insertRndTaxon(datasetKey));
    ids.add(insertRndTaxon(datasetKey));
    ids.add(insertRndTaxon(datasetKey));
    ids.add(insertRndTaxon(datasetKey));
    ids.add(insertRndTaxon(datasetKey));
    ids.add(insertRndTaxon(datasetKey));
    refreshIndex(client, indexName());

    NameUsageSearchRequest nsr = new NameUsageSearchRequest();

    nsr.addFilter(NameUsageSearchParameter.DATASET_KEY, datasetKey);
    nsr.addFilter(NameUsageSearchParameter.USAGE_ID, ids.get(0));
    ResultPage<NameUsageWrapper> result = svc.search(indexName(), nsr, new Page());
    assertEquals(1, result.getResult().size());
    assertEquals(ids.get(0), result.getResult().get(0).getUsage().getId());
    assertEquals(datasetKey, (int) result.getResult().get(0).getUsage().getDatasetKey());

    nsr.addFilter(NameUsageSearchParameter.USAGE_ID, ids.get(2));
    nsr.addFilter(NameUsageSearchParameter.USAGE_ID, ids.get(4));
    result = svc.search(indexName(), nsr, new Page());
    assertEquals(3, result.getResult().size());
  }

  @Test
  public void testNameFieldsQuery1() throws IOException {
    NameUsageWrapperConverter converter = new NameUsageWrapperConverter();

    // Find all documents where the uninomial field is not empty
    NameUsageSearchRequest nsr = new NameUsageSearchRequest();
    nsr.setHighlight(false);
    nsr.addFilter(NameUsageSearchParameter.FIELD, "uninomial");

    // Match
    Name n = new Name();
    n.setUninomial("laridae");
    BareName bn = new BareName(n);
    NameUsageWrapper nuw1 = new NameUsageWrapper(bn);
    insert(client, indexName(), converter.toDocument(deepCopy(nuw1)));

    // Match
    n = new Name();
    n.setUninomial("parus");
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(bn);
    insert(client, indexName(), converter.toDocument(deepCopy(nuw2)));

    // No match
    n = new Name();
    n.setUninomial(null);
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw3 = new NameUsageWrapper(bn);
    insert(client, indexName(), converter.toDocument(deepCopy(nuw3)));

    // Match
    n = new Name();
    n.setUninomial("parus");
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw4 = new NameUsageWrapper(bn);
    insert(client, indexName(), converter.toDocument(deepCopy(nuw4)));

    refreshIndex(client, indexName());

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw4);

    ResultPage<NameUsageWrapper> result = svc.search(indexName(), nsr, new Page());
    assertEquals(expected, result.getResult());
  }

  @Test
  public void testNameFieldsQuery2() throws IOException {
    NameUsageWrapperConverter converter = new NameUsageWrapperConverter();

    // Find all documents where the uninomial field is not empty
    NameUsageSearchRequest nsr = new NameUsageSearchRequest();
    nsr.setHighlight(false);
    nsr.addFilter(NameUsageSearchParameter.FIELD, "uninomial", "remarks", "specific_epithet");

    // Match
    Name n = new Name();
    n.setUninomial("laridae");
    BareName bn = new BareName(n);
    NameUsageWrapper nuw1 = new NameUsageWrapper(bn);
    insert(client, indexName(), converter.toDocument(deepCopy(nuw1)));

    // Match
    n = new Name();
    n.setUninomial("parus");
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(bn);
    insert(client, indexName(), converter.toDocument(deepCopy(nuw2)));

    // No match
    n = new Name();
    n.setUninomial(null);
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw3 = new NameUsageWrapper(bn);
    insert(client, indexName(), converter.toDocument(deepCopy(nuw3)));

    // Match
    n = new Name();
    n.setUninomial("parus");
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw4 = new NameUsageWrapper(bn);
    insert(client, indexName(), converter.toDocument(deepCopy(nuw4)));

    refreshIndex(client, indexName());

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw4);
    ResultPage<NameUsageWrapper> result = svc.search(indexName(), nsr, new Page());
    assertEquals(expected, result.getResult());
  }

  @Test
  public void testNameFieldsQuery3() throws IOException {

    // Find all documents where the uninomial field is not empty
    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setHighlight(false);
    query.addFilter(NameUsageSearchParameter.FIELD, "uninomial", "remarks", "specific_epithet");

    index(testNameFieldsQuery3_data());

    refreshIndex(client, indexName());

    List<NameUsageWrapper> expected = new ArrayList<>(testNameFieldsQuery3_data());
    expected.remove(3);

    ResultPage<NameUsageWrapper> result = svc.search(indexName(), query, new Page());
    assertEquals(expected, result.getResult());

  }

  private static List<NameUsageWrapper> testNameFieldsQuery3_data() {
    // Match
    Name n = new Name();
    n.setUninomial("laridae");
    BareName bn = new BareName(n);
    NameUsageWrapper nuw1 = new NameUsageWrapper(bn);
    nuw1.setId("11111");

    // Match
    n = new Name();
    n.setUninomial("parus");
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(bn);
    nuw2.setId("22222");

    // Match
    n = new Name();
    n.setUninomial(null);
    n.setGenus("parus");
    n.setSpecificEpithet("major");
    n.setRemarks("A bird");
    bn = new BareName(n);
    NameUsageWrapper nuw3 = new NameUsageWrapper(bn);
    nuw3.setId("33333");

    // No Match
    n = new Name();
    n.setUninomial(null);
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw4 = new NameUsageWrapper(bn);
    nuw4.setId("44444");

    // Match
    n = new Name();
    n.setUninomial(null);
    n.setGenus("parus");
    n.setRemarks("A bird");
    bn = new BareName(n);
    NameUsageWrapper nuw5 = new NameUsageWrapper(bn);
    nuw5.setId("55555");

    return List.of(nuw1, nuw2, nuw3, nuw4, nuw5);

  }

  @Test
  @Ignore("Fails since https://github.com/Sp2000/colplus-backend/commit/76ac785a29dc39054859a4471e2dbb20bbc9de8b")
  public void testWithBigQ() {
    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setHighlight(false);
    query.setQ("ABCDEFGHIJKLMNOPQRSTUVW");
    List<NameUsageWrapper> documents = testWithBigQ_data();
    index(documents);
    NameUsageSearchResponse response = search(query);
    assertEquals(2, response.getResult().size());
  }

  private List<NameUsageWrapper> testWithBigQ_data() {

    List<NameUsageWrapper> usages = createNameUsages(4);

    // Match on scientific name
    usages.get(0).getUsage().getName().setSpecificEpithet("ABCDEFGHIJKLMNOPQRSTUVWXYZ");

    // No match (missing 'W')
    usages.get(2).getUsage().getName().setSpecificEpithet("ABCDEFGHIJKLMNOPQRSTUV");

    // No match (missing 'A')
    usages.get(3).getUsage().getName().setSpecificEpithet("BCDEFGHIJKLMNOPQRSTUVW");

    return usages;

  }

  // Issue #207
  @Test
  public void testWithSmthii__1() {
    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setSearchType(SearchType.PREFIX);
    query.setHighlight(false);
    query.setQ("Smithi");
    query.setFuzzy(true);
    index(testWithSmthii_data());
    // Expect all to come back
    NameUsageSearchResponse result = search(query);
    assertEquals(testWithSmthii_data(), result.getResult());
  }

  // https://github.com/CatalogueOfLife/backend/issues/864
  @Test
  public void authorshipNavas() {
    indexNewTaxon(Rank.SPECIES, "Eatonica", "schoutedeni", "Navás");
    indexNewTaxon(Rank.SPECIES, "Eatonica", "maxima", "(Navás, 1911) Miller");
    indexNewTaxon(Rank.SPECIES, "Eatonica", "markii", "(Navás, 1911)");
    indexNewTaxon(Rank.SPECIES, "Eatonica", "minima", "Navas, 1911");

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setSearchType(SearchType.WHOLE_WORDS);
    query.setQ("Eatonica");
    NameUsageSearchResponse result = search(query);
    assertEquals(4, result.getResult().size());

    query.setQ("Navas");
    assertEquals(4, search(query).getResult().size());

    query.setQ("1911");
    assertEquals(3, search(query).getResult().size());

    query.setQ("Miller");
    assertEquals(1, search(query).getResult().size());

    query.setQ("Navás");
    assertEquals(4, search(query).getResult().size());

    query.setQ("Nävas");
    assertEquals(4, search(query).getResult().size());
  }

  @Test
  public void testWithSmthii__2() {
    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setSearchType(SearchType.PREFIX);
    query.setHighlight(false);
    query.setQ("Smithii");
    query.setFuzzy(true);
    index(testWithSmthii_data());
    // Expect all to come back
    NameUsageSearchResponse result = search(query);
    assertEquals(testWithSmthii_data(), result.getResult());
  }

  private static List<NameUsageWrapper> testWithSmthii_data() {
    Name n = new Name();
    n.setSpecificEpithet("Smithii");
    BareName bn = new BareName(n);
    NameUsageWrapper nuw1 = new NameUsageWrapper(bn);

    n = new Name();
    n.setSpecificEpithet("Smithi");
    bn = new BareName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(bn);

    n = new Name();
    n.setSpecificEpithet("SmithiiFooBar");
    bn = new BareName(n);
    NameUsageWrapper nuw3 = new NameUsageWrapper(bn);

    n = new Name();
    n.setSpecificEpithet("SmithiFooBar");
    bn = new BareName(n);
    NameUsageWrapper nuw4 = new NameUsageWrapper(bn);

    return Arrays.asList(nuw1, nuw2, nuw3, nuw4);
  }

  @Test
  public void authorshipOnlySearch() {

    // Define search
    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setHighlight(false);
    // Only search in authorship field
    query.setContent(EnumSet.of(NameUsageSearchRequest.SearchContent.AUTHORSHIP));
    query.setQ("UNLIKE");

    // No match
    NameUsageWrapper nuw1 = minimalTaxon();
    nuw1.getUsage().getName().setScientificName("AN UNLIKELY NAME");
    index(nuw1);

    // No match
    NameUsageWrapper nuw2 = minimalTaxon();
    nuw2.getUsage().getName().setScientificName("AN UNLIKELY NAME");
    index(nuw2);

    // No match
    NameUsageWrapper nuw3 = minimalTaxon();
    nuw3.getUsage().getName().setScientificName("AN UNLIKELY NAME");
    index(nuw3);

    // No match
    NameUsageWrapper nuw4 = minimalTaxon();
    nuw4.getUsage().getName().setScientificName("it's unlike capital case");
    index(nuw4);

    // No match
    NameUsageWrapper nuw5 = minimalTaxon();
    nuw5.getUsage().getName().setScientificName("LIKE IT OR NOT");
    index(nuw5);

    ResultPage<NameUsageWrapper> result = search(query);

    assertEquals(0, result.getResult().size());
  }

  static <T extends NameUsageBase> T fill (T nu, String id) {
    nu.setId(id);
    nu.setDatasetKey(100);
    nu.setAccordingToId("r0");
    nu.setLink(URI.create("https://gbif.org"));
    nu.setNamePhrase("s.l.");
    nu.setOrigin(Origin.SOURCE);
    nu.setParentId("1");
    nu.setReferenceIds(List.of("r1", "r2", "r3"));
    nu.setRemarks("My eternal remarks");
    nu.setSectorKey(13);
    nu.setVerbatimKey(999);

    if (nu.isTaxon()) {
      Taxon t = (Taxon) nu;
      nu.setStatus(TaxonomicStatus.ACCEPTED);
      t.setEnvironments(Set.of(Environment.MARINE, Environment.BRACKISH));
      t.setExtinct(true);
      t.setScrutinizer("M.D.Hernett");
      t.setScrutinizerDate(FuzzyDate.now());
      t.setTemporalRangeStart("Jura");
      t.setTemporalRangeEnd("Kreide");
    } else if (nu.isSynonym()) {
      Synonym s = (Synonym) nu;
      s.setStatus(TaxonomicStatus.SYNONYM);
    }
    Name n = nu.getName();
    n.setId("n" + id);
    n.setDatasetKey(nu.getDatasetKey());
    n.setGenus("Abies");
    n.setSpecificEpithet("alba");
    n.setInfraspecificEpithet("montana");
    n.setRank(Rank.SUBSPECIES);
    n.setCombinationAuthorship(Authorship.yearAuthors("1879", "Smith", "Miller"));
    n.rebuildScientificName();
    n.rebuildAuthorship();
    return nu;
  }

  static NameUsageWrapper fill (NameUsageWrapper nuw, String id) {
    fill((NameUsageBase) nuw.getUsage(), id);
    return nuw;
  }

  static NameUsageWrapper fill (NameUsageWrapper nuw, String id, Taxon accepted) {
    fill((NameUsageBase) nuw.getUsage(), id);
    ((Synonym)nuw.getUsage()).setAccepted(accepted);
    return nuw;
  }

  /**
   * https://github.com/CatalogueOfLife/backend/issues/842
   */
  @Test
  public void missappliedNames() {
    // content
    NameUsageWrapper nuw1 = fill(minimalTaxon(), "t1");
    Taxon acc = (Taxon) nuw1.getUsage();

    NameUsageWrapper syn = fill(minimalSynonym(), "s1", acc);

    NameUsageWrapper mis = fill(minimalSynonym(), "mis", acc);
    mis.getUsage().setNamePhrase("non Miller 1879");
    mis.getUsage().setStatus(TaxonomicStatus.MISAPPLIED);

    NameUsageWrapper mis2 = fill(minimalSynonym(), "mis2", acc);
    mis2.getUsage().setNamePhrase(null);
    mis2.getUsage().setAccordingToId("r1");
    ((Synonym)mis2.getUsage()).setAccordingTo("Miller 1901");
    mis2.getUsage().setStatus(TaxonomicStatus.MISAPPLIED);

    index(nuw1, syn, mis, mis2);

    // search
    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.addFilter(NameUsageSearchParameter.STATUS, TaxonomicStatus.MISAPPLIED);
    ResultPage<NameUsageWrapper> result = search(query);
    assertEquals(2, result.getResult().size());


    query = new NameUsageSearchRequest();
    query.addFilter(NameUsageSearchParameter.DATASET_KEY, mis.getUsage().getDatasetKey());
    query.addFilter(NameUsageSearchParameter.USAGE_ID, mis.getUsage().getId());
    result = search(query);
    assertEquals(1, result.getResult().size());
    Synonym s = (Synonym) result.getResult().get(0).getUsage();
    assertEquals("non Miller 1879", s.getNamePhrase());
    assertEquals("Abies alba montana Smith & Miller, 1879 non Miller 1879", s.getLabel());

    query.setFilter(NameUsageSearchParameter.USAGE_ID, mis2.getUsage().getId());
    result = search(query);
    assertEquals(1, result.getResult().size());
    s = (Synonym) result.getResult().get(0).getUsage();
    assertNull(s.getNamePhrase());
    assertEquals("Miller 1901", s.getAccordingTo());
    assertEquals("Abies alba montana Smith & Miller, 1879 sensu Miller 1901", s.getLabel());
  }

}
