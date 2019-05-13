package org.col.es;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import org.col.api.TestEntityGenerator;
import org.col.api.model.BareName;
import org.col.api.model.Name;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.model.Taxon;
import org.col.api.model.VernacularName;
import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchRequest.SearchContent;
import org.col.api.search.NameUsageWrapper;
import org.col.api.vocab.Issue;
import org.col.api.vocab.TaxonomicStatus;
import org.col.es.model.EsNameUsage;
import org.elasticsearch.client.RestClient;
import org.gbif.nameparser.api.Rank;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.col.es.EsUtil.insert;
import static org.col.es.EsUtil.refreshIndex;
import static org.junit.Assert.assertEquals;

public class NameUsageSearchServiceTest extends EsReadTestBase {

  private static RestClient client;
  private static NameUsageSearchService svc;

  @BeforeClass
  public static void init() {
    client = esSetupRule.getEsClient();
    svc = new NameUsageSearchService(indexName, esSetupRule.getEsClient());
  }

  @AfterClass
  public static void shutdown() throws IOException {
    // EsUtil.deleteIndex(client, indexName);
    client.close();
  }

  @Before
  public void before() throws IOException {
    EsUtil.deleteIndex(client, indexName);
    EsUtil.createIndex(client, indexName, getEsConfig().nameUsage);
  }

  @Test
  public void testQuery1() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setHighlight(false);
    nsr.addFilter(NameSearchParameter.ISSUE, Issue.ACCEPTED_NAME_MISSING);

    // Match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING));
    insert(client, indexName, transfer.toDocument(nuw1));

    // Match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCORDING_TO_DATE_INVALID));
    insert(client, indexName, transfer.toDocument(nuw2));

    // Match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName, transfer.toDocument(nuw3));

    // No match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(null);
    insert(client, indexName, transfer.toDocument(nuw4));

    // No match
    NameUsageWrapper nuw5 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw5.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName, transfer.toDocument(nuw5));

    // No match
    NameUsageWrapper nuw6 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw6.setIssues(EnumSet.of(Issue.CITATION_UNPARSED, Issue.BASIONYM_ID_INVALID));
    insert(client, indexName, transfer.toDocument(nuw6));

    // No match
    NameUsageWrapper nuw7 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw7.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName, transfer.toDocument(nuw7));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(3, result.getResult().size());
  }

  @Test
  public void testQuery2() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Find all documents with an issue of either ACCEPTED_NAME_MISSING or ACCORDING_TO_DATE_INVALID
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFilter(NameSearchParameter.ISSUE, EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCORDING_TO_DATE_INVALID));

    // Match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING));
    insert(client, indexName, transfer.toDocument(nuw1));

    // Match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCORDING_TO_DATE_INVALID));
    insert(client, indexName, transfer.toDocument(nuw2));

    // Match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName, transfer.toDocument(nuw3));

    // No match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(null);
    insert(client, indexName, transfer.toDocument(nuw4));

    // No match
    NameUsageWrapper nuw5 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw5.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName, transfer.toDocument(nuw5));

    // No match
    NameUsageWrapper nuw6 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw6.setIssues(EnumSet.of(Issue.CITATION_UNPARSED, Issue.BASIONYM_ID_INVALID));
    insert(client, indexName, transfer.toDocument(nuw6));

    // No match
    NameUsageWrapper nuw7 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw7.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName, transfer.toDocument(nuw7));

    // No match
    NameUsageWrapper nuw8 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw8.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.DOUBTFUL_NAME));
    insert(client, indexName, transfer.toDocument(nuw8));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(4, result.getResult().size());
  }

  @Test
  public void testQuery3() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Find all documents with an issue of any of ACCEPTED_NAME_MISSING, ACCORDING_TO_DATE_INVALID, BASIONYM_ID_INVALID
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setHighlight(false);
    nsr.addFilter(NameSearchParameter.ISSUE,
        Issue.ACCEPTED_NAME_MISSING, Issue.ACCORDING_TO_DATE_INVALID, Issue.BASIONYM_ID_INVALID);

    // Match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING));
    insert(client, indexName, transfer.toDocument(nuw1));

    // Match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCORDING_TO_DATE_INVALID));
    insert(client, indexName, transfer.toDocument(nuw2));

    // Match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName, transfer.toDocument(nuw3));

    // No match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(null);
    insert(client, indexName, transfer.toDocument(nuw4));

    // No match
    NameUsageWrapper nuw5 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw5.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName, transfer.toDocument(nuw5));

    // Match
    NameUsageWrapper nuw6 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw6.setIssues(EnumSet.of(Issue.CITATION_UNPARSED, Issue.BASIONYM_ID_INVALID));
    insert(client, indexName, transfer.toDocument(nuw6));

    // No match
    NameUsageWrapper nuw7 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw7.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName, transfer.toDocument(nuw7));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(4, result.getResult().size());
  }

  @Test
  public void autocomplete1() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setHighlight(false);
    nsr.setQ("UNLIKE");

    // Match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    List<String> vernaculars = Arrays.asList("AN UNLIKELY NAME");
    nuw1.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toDocument(nuw1));

    // Match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("ANOTHER NAME", "AN UNLIKELY NAME");
    nuw2.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toDocument(nuw2));

    // Match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("YET ANOTHER NAME", "ANOTHER NAME", "AN UNLIKELY NAME");
    nuw3.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toDocument(nuw3));

    // Match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("it's unlike capital case");
    nuw4.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toDocument(nuw4));

    // No match
    NameUsageWrapper nuw5 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("LIKE IT OR NOT");
    nuw5.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toDocument(nuw5));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(4, result.getResult().size());
  }

  @Test
  public void autocomplete2() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setHighlight(false);
    // Only search in authorship field
    nsr.setContent(EnumSet.of(NameSearchRequest.SearchContent.AUTHORSHIP));
    nsr.setQ("UNLIKE");

    // No match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    List<String> vernaculars = Arrays.asList("AN UNLIKELY NAME");
    nuw1.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toDocument(nuw1));

    // No match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("ANOTHER NAME", "AN UNLIKELY NAME");
    nuw2.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toDocument(nuw2));

    // No match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("YET ANOTHER NAME", "ANOTHER NAME", "AN UNLIKELY NAME");
    nuw3.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toDocument(nuw3));

    // No match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("it's unlike capital case");
    nuw4.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toDocument(nuw4));

    // No match
    NameUsageWrapper nuw5 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("LIKE IT OR NOT");
    nuw5.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toDocument(nuw5));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(0, result.getResult().size());
  }

  @Test
  public void testIsNull() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Define search condition
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setHighlight(false);
    nsr.addFilter(NameSearchParameter.ISSUE, NameSearchRequest.IS_NULL);

    // Match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName, transfer.toDocument(nuw1));
    // No match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName, transfer.toDocument(nuw2));
    // Match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(null);
    insert(client, indexName, transfer.toDocument(nuw3));
    // No match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName, transfer.toDocument(nuw4));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(2, result.getResult().size());
  }

  @Test
  public void testIsNotNull() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Define search condition
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFilter(NameSearchParameter.ISSUE, NameSearchRequest.IS_NOT_NULL);

    // No match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName, transfer.toDocument(nuw1));
    // Match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName, transfer.toDocument(nuw2));
    // No match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(null);
    insert(client, indexName, transfer.toDocument(nuw3));
    // Match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName, transfer.toDocument(nuw4));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(2, result.getResult().size());
  }

  @Test
  public void testNameFieldsQuery1() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Find all documents where the uninomial field is not empty
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setHighlight(false);
    nsr.addFilter(NameSearchParameter.FIELD, "uninomial");

    // Match
    Name n = new Name();
    n.setUninomial("laridae");
    BareName bn = new BareName(n);
    NameUsageWrapper nuw1 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toDocument(nuw1));

    // Match
    n = new Name();
    n.setUninomial("parus");
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toDocument(nuw2));

    // No match
    n = new Name();
    n.setUninomial(null);
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw3 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toDocument(nuw3));

    // Match
    n = new Name();
    n.setUninomial("parus");
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw4 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toDocument(nuw4));

    refreshIndex(client, indexName);

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw4);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(expected, result.getResult());

  }

  @Test
  public void testNameFieldsQuery2() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Find all documents where the uninomial field is not empty
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setHighlight(false);
    nsr.addFilter(NameSearchParameter.FIELD, "uninomial", "remarks", "specific_epithet");

    // Match
    Name n = new Name();
    n.setUninomial("laridae");
    BareName bn = new BareName(n);
    NameUsageWrapper nuw1 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toDocument(nuw1));

    // Match
    n = new Name();
    n.setUninomial("parus");
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toDocument(nuw2));

    // No match
    n = new Name();
    n.setUninomial(null);
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw3 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toDocument(nuw3));

    // Match
    n = new Name();
    n.setUninomial("parus");
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw4 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toDocument(nuw4));

    refreshIndex(client, indexName);

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw4);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(expected, result.getResult());

  }

  @Test
  public void testNameFieldsQuery3() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Find all documents where the uninomial field is not empty
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setHighlight(false);
    nsr.addFilter(NameSearchParameter.FIELD, "uninomial", "remarks", "specific_epithet");

    // Match
    Name n = new Name();
    n.setUninomial("laridae");
    BareName bn = new BareName(n);
    NameUsageWrapper nuw1 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toDocument(nuw1));

    // Match
    n = new Name();
    n.setUninomial("parus");
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toDocument(nuw2));

    // Match
    n = new Name();
    n.setUninomial(null);
    n.setGenus("parus");
    n.setSpecificEpithet("major");
    n.setRemarks("A bird");
    bn = new BareName(n);
    NameUsageWrapper nuw3 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toDocument(nuw3));

    // No Match
    n = new Name();
    n.setUninomial(null);
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw4 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toDocument(nuw4));

    // Match
    n = new Name();
    n.setUninomial(null);
    n.setGenus("parus");
    n.setRemarks("A bird");
    bn = new BareName(n);
    NameUsageWrapper nuw5 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toDocument(nuw5));

    refreshIndex(client, indexName);

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw3, nuw5);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(expected, result.getResult());

  }

  @Test
  public void testMultipleFiltersAndQ() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setHighlight(false);
    nsr.setQ("larid");
    nsr.setContent(EnumSet.of(SearchContent.SCIENTIFIC_NAME, SearchContent.AUTHORSHIP));
    // Find all documents where the uninomial field is not empty
    nsr.addFilter(NameSearchParameter.FIELD, "uninomial");
    nsr.addFilter(NameSearchParameter.RANK, Rank.ORDER, Rank.FAMILY);
    nsr.addFilter(NameSearchParameter.STATUS, TaxonomicStatus.ACCEPTED);

    List<NameUsageWrapper> usages = testMultipleFiltersAndQ__createTestData();
    for (NameUsageWrapper nuw : usages) {
      insert(client, indexName, transfer.toDocument(nuw));
    }
    // Watch out, after this, the name usages are not the same anymore; they will have been pruned.

    refreshIndex(client, indexName);

    // So again:
    usages = testMultipleFiltersAndQ__createTestData();

    List<NameUsageWrapper> expected = usages.subList(0, 2);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(expected, result.getResult());

  }

  private static List<NameUsageWrapper> testMultipleFiltersAndQ__createTestData() {
    // Match
    Name n = new Name();
    n.setScientificName("laridae");
    n.setUninomial("laridae");
    n.setRank(Rank.FAMILY);
    Taxon t = new Taxon();
    t.setId("AAA");
    t.setName(n);
    t.setStatus(TaxonomicStatus.ACCEPTED);
    VernacularName vn = new VernacularName();
    vn.setName("laridae");
    NameUsageWrapper nuw1 = new NameUsageWrapper(t);
    // Present or not shouldn't make a difference because of SearchContent
    nuw1.setVernacularNames(Arrays.asList(vn));

    // Match
    n = new Name();
    n.setScientificName("laridae");
    n.setUninomial("laridae");
    n.setRank(Rank.ORDER);
    t = new Taxon();
    t.setId("BBB");
    t.setName(n);
    t.setStatus(TaxonomicStatus.ACCEPTED);
    vn = new VernacularName();
    vn.setName("laridae");
    NameUsageWrapper nuw2 = new NameUsageWrapper(t);
    nuw2.setVernacularNames(Arrays.asList(vn));

    // No match
    n = new Name();
    n.setScientificName("xxx"); // <---
    n.setUninomial("laridae");
    n.setRank(Rank.FAMILY);
    t = new Taxon();
    t.setId("CCC");
    t.setName(n);
    t.setStatus(TaxonomicStatus.ACCEPTED);
    vn = new VernacularName();
    vn.setName("laridae");
    NameUsageWrapper nuw3 = new NameUsageWrapper(t);
    nuw3.setVernacularNames(Arrays.asList(vn));

    // No match
    n = new Name();
    n.setScientificName("laridae");
    n.setUninomial(null); // <---
    n.setRank(Rank.FAMILY);
    t = new Taxon();
    t.setId("DDD");
    t.setName(n);
    t.setStatus(TaxonomicStatus.ACCEPTED);
    vn = new VernacularName();
    vn.setName("laridae");
    NameUsageWrapper nuw4 = new NameUsageWrapper(t);
    nuw4.setVernacularNames(Arrays.asList(vn));

    // No match
    n = new Name();
    n.setScientificName("laridae");
    n.setUninomial(null);
    n.setRank(Rank.GENUS); // <---
    t = new Taxon();
    t.setId("EEE");
    t.setName(n);
    t.setStatus(TaxonomicStatus.ACCEPTED);
    vn = new VernacularName();
    vn.setName("laridae");
    NameUsageWrapper nuw5 = new NameUsageWrapper(t);
    nuw5.setVernacularNames(Arrays.asList(vn));

    // No match
    n = new Name();
    n.setScientificName("laridae");
    n.setUninomial("laridae");
    n.setRank(Rank.FAMILY);
    t = new Taxon();
    t.setId("FFF");
    t.setName(n);
    t.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    vn = new VernacularName();
    vn.setName("laridae");
    NameUsageWrapper nuw6 = new NameUsageWrapper(t);
    nuw6.setVernacularNames(Arrays.asList(vn));

    return Arrays.asList(nuw1, nuw2, nuw3, nuw4, nuw5, nuw6);

  }

  @Test
  public void testWithBigQString() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Find all documents where the uninomial field is not empty
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setHighlight(false);
    nsr.setQ("ABCDEFGHIJKLMNOPQRSTUVW");

    // Match on scientific name
    Name n = new Name();
    n.setScientificName("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
    BareName bn = new BareName(n);
    NameUsageWrapper nuw1 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toDocument(nuw1));

    // Match on vernacular name
    n = new Name();
    n.setScientificName("FOO");
    bn = new BareName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(bn);
    VernacularName vn = new VernacularName();
    vn.setName("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
    nuw2.setVernacularNames(Arrays.asList(vn));
    insert(client, indexName, transfer.toDocument(nuw2));

    // Match on authorship
    n = new Name();
    n.setScientificName("BAR");
    bn = new BareName(n);
    NameUsageWrapper nuw3 = new NameUsageWrapper(bn);
    // Bypassing dark art of authorship generation here
    EsNameUsage esn = transfer.toDocument(nuw3);
    esn.setAuthorship("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
    insert(client, indexName, esn);

    // No match (missing 'W')
    n = new Name();
    n.setScientificName("ABCDEFGHIJKLMNOPQRSTUV");
    bn = new BareName(n);
    NameUsageWrapper nuw4 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toDocument(nuw4));

    // No match (missing 'A')
    n = new Name();
    n.setScientificName("BCDEFGHIJKLMNOPQRSTUVW");
    bn = new BareName(n);
    NameUsageWrapper nuw5 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toDocument(nuw5));

    refreshIndex(client, indexName);

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw3);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(expected, result.getResult());

  }

  // Issue #207
  @Test
  public void testWithSmthii__1() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setHighlight(false);
    nsr.setQ("Smithi");

    List<NameUsageWrapper> usages = testWithSmthii__createTestData();
    for (NameUsageWrapper nuw : usages) {
      insert(client, indexName, transfer.toDocument(nuw));
    }

    refreshIndex(client, indexName);

    // Expect all to come back
    List<NameUsageWrapper> expected = testWithSmthii__createTestData();

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(expected, result.getResult());

  }

  @Test
  public void testWithSmthii__2() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setHighlight(false);
    nsr.setQ("Smithii");

    Name n = new Name();
    n.setScientificName("Smithii");
    BareName bn = new BareName(n);
    NameUsageWrapper nuw1 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toDocument(nuw1));

    n = new Name();
    n.setScientificName("Smithi");
    bn = new BareName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toDocument(nuw2));

    n = new Name();
    n.setScientificName("SmithiiFooBar");
    bn = new BareName(n);
    NameUsageWrapper nuw3 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toDocument(nuw3));

    n = new Name();
    n.setScientificName("SmithiFooBar");
    bn = new BareName(n);
    NameUsageWrapper nuw4 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toDocument(nuw4));

    refreshIndex(client, indexName);

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw3, nuw4);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(expected, result.getResult());

  }

  private static List<NameUsageWrapper> testWithSmthii__createTestData() {
    Name n = new Name();
    n.setScientificName("Smithii");
    BareName bn = new BareName(n);
    NameUsageWrapper nuw1 = new NameUsageWrapper(bn);

    n = new Name();
    n.setScientificName("Smithi");
    bn = new BareName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(bn);

    n = new Name();
    n.setScientificName("SmithiiFooBar");
    bn = new BareName(n);
    NameUsageWrapper nuw3 = new NameUsageWrapper(bn);

    n = new Name();
    n.setScientificName("SmithiFooBar");
    bn = new BareName(n);
    NameUsageWrapper nuw4 = new NameUsageWrapper(bn);

    return Arrays.asList(nuw1, nuw2, nuw3, nuw4);
  }

  private static List<VernacularName> create(List<String> names) {
    return names.stream().map(n -> {
      VernacularName vn = new VernacularName();
      vn.setName(n);
      return vn;
    }).collect(Collectors.toList());
  }
}
