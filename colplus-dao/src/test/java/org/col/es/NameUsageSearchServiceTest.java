package org.col.es;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.col.api.TestEntityGenerator;
import org.col.api.model.BareName;
import org.col.api.model.Name;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.model.Synonym;
import org.col.api.model.Taxon;
import org.col.api.model.VernacularName;
import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchRequest.SearchContent;
import org.col.api.search.NameSearchRequest.SortBy;
import org.col.api.search.NameUsageWrapper;
import org.col.api.vocab.Issue;
import org.col.api.vocab.TaxonomicStatus;
import org.col.es.model.EsNameUsage;
import org.col.es.query.EsSearchRequest;
import org.col.es.query.SortField;
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

  private static final String indexName = "name_usage_test";

  private static RestClient client;
  private static NameUsageSearchService svc;

  @BeforeClass
  public static void init() {
    client = esSetupRule.getEsClient();
    svc = new NameUsageSearchService(esSetupRule.getEsClient());
  }

  @AfterClass
  public static void shutdown() throws IOException {
    // EsUtil.deleteIndex(client, indexName);
    client.close();
  }

  @Before
  public void before() {
    EsUtil.deleteIndex(client, indexName);
    EsUtil.createIndex(client, indexName, getEsConfig().nameUsage);
  }

  @Test
  public void testSort1() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();
    EsNameUsage enu = transfer.toEsDocument(TestEntityGenerator.newNameUsageTaxonWrapper());
    insert(client, indexName, enu);
    enu = transfer.toEsDocument(TestEntityGenerator.newNameUsageSynonymWrapper());
    insert(client, indexName, enu);
    enu = transfer.toEsDocument(TestEntityGenerator.newNameUsageBareNameWrapper());
    insert(client, indexName, enu);
    refreshIndex(client, indexName);
    assertEquals(3, EsUtil.count(client, indexName));
    NameSearchRequest nsr = new NameSearchRequest();
    // Force sorting by index order
    nsr.setSortBy(null);
    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());
    assertEquals(3, result.getResult().size());
    assertEquals(Taxon.class, result.getResult().get(0).getUsage().getClass());
    assertEquals(Synonym.class, result.getResult().get(1).getUsage().getClass());
    assertEquals(BareName.class, result.getResult().get(2).getUsage().getClass());
  }

  @Test
  public void testSort2() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();
    EsNameUsage enu = transfer.toEsDocument(TestEntityGenerator.newNameUsageTaxonWrapper());
    // Overwrite to test ordering by scientific name
    enu.setScientificNameWN("B");
    insert(client, indexName, enu);
    enu = transfer.toEsDocument(TestEntityGenerator.newNameUsageSynonymWrapper());
    enu.setScientificNameWN("C");
    insert(client, indexName, enu);
    enu = transfer.toEsDocument(TestEntityGenerator.newNameUsageBareNameWrapper());
    enu.setScientificNameWN("A");
    insert(client, indexName, enu);
    refreshIndex(client, indexName);
    assertEquals(3, EsUtil.count(client, indexName));
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setSortBy(SortBy.NAME);
    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());
    assertEquals(3, result.getResult().size());
    assertEquals(BareName.class, result.getResult().get(0).getUsage().getClass());
    assertEquals(Taxon.class, result.getResult().get(1).getUsage().getClass());
    assertEquals(Synonym.class, result.getResult().get(2).getUsage().getClass());
  }

  @Test
  public void testSortDescending() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    EsSearchRequest esr = EsSearchRequest.emptyRequest();
    esr.setSort(Arrays.asList(new SortField("scientificNameWN", false), new SortField("rank", false)));

    // Create name usage in the order we expect them to come out, then shuffle.
    Name n = new Name();
    n.setScientificName("C");
    n.setRank(Rank.SPECIES);
    Taxon t = new Taxon();
    t.setName(n);
    final NameUsageWrapper nuw1 = new NameUsageWrapper(t);

    n = new Name();
    n.setScientificName("B");
    n.setRank(Rank.GENUS);
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(t);

    n = new Name();
    n.setScientificName("B");
    n.setRank(Rank.PHYLUM);
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper nuw3 = new NameUsageWrapper(t);

    n = new Name();
    n.setScientificName("A");
    n.setRank(Rank.INFRASPECIFIC_NAME);
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper nuw4 = new NameUsageWrapper(t);

    List<NameUsageWrapper> all = Arrays.asList(nuw1, nuw2, nuw3, nuw4);

    List<NameUsageWrapper> shuffled = new ArrayList<>(all);
    Collections.shuffle(shuffled);
    shuffled.stream().map(t1 -> {
      try {
        return transfer.toEsDocument(t1);
      } catch (JsonProcessingException e) {
        throw new RuntimeException();
      }
    }).forEach(x -> insert(client, indexName, x));
    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, esr, new Page());

    assertEquals(all, result.getResult());
  }

  @Test
  public void testSortTaxonomic() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setSortBy(SortBy.TAXONOMIC);

    // Don't forget this one; we're going to insert more than 10 docs
    Page page = new Page(100);

    // Create name usage in the order we expect them to come out, then shuffle.
    Name n = new Name();
    n.setRank(Rank.KINGDOM);
    n.setScientificName("B");
    Taxon t = new Taxon();
    t.setName(n);
    final NameUsageWrapper nuw1 = new NameUsageWrapper(t);

    n = new Name();
    n.setRank(Rank.KINGDOM);
    n.setScientificName("Z");
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(t);

    n = new Name();
    n.setRank(Rank.PHYLUM);
    n.setScientificName("C");
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper nuw3 = new NameUsageWrapper(t);

    n = new Name();
    n.setRank(Rank.PHYLUM);
    n.setScientificName("E");
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper nuw4 = new NameUsageWrapper(t);

    n = new Name();
    n.setRank(Rank.CLASS);
    n.setScientificName("Y");
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper nuw5 = new NameUsageWrapper(t);

    n = new Name();
    n.setRank(Rank.ORDER);
    n.setScientificName("X");
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper nuw6 = new NameUsageWrapper(t);

    n = new Name();
    n.setRank(Rank.FAMILY);
    n.setScientificName("V");
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper nuw7 = new NameUsageWrapper(t);

    n = new Name();
    n.setRank(Rank.GENUS);
    n.setScientificName("Q");
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper nuw8 = new NameUsageWrapper(t);

    n = new Name();
    n.setRank(Rank.SPECIES);
    n.setScientificName("K");
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper nuw9 = new NameUsageWrapper(t);

    n = new Name();
    n.setRank(Rank.SPECIES);
    n.setScientificName("L");
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper nuw10 = new NameUsageWrapper(t);

    n = new Name();
    n.setRank(Rank.SPECIES);
    n.setScientificName("M");
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper nuw11 = new NameUsageWrapper(t);

    List<NameUsageWrapper> all = Arrays.asList(nuw1, nuw2, nuw3, nuw4, nuw5, nuw6, nuw7, nuw8, nuw9, nuw10, nuw11);

    List<NameUsageWrapper> shuffled = new ArrayList<>(all);
    Collections.shuffle(shuffled);
    shuffled.stream().map(t1 -> {
      try {
        return transfer.toEsDocument(t1);
      } catch (JsonProcessingException e) {
        throw new RuntimeException();
      }
    }).forEach(x -> insert(client, indexName, x));
    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, page);

    assertEquals(all, result.getResult());

  }

  @Test
  public void testQuery1() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFilter(NameSearchParameter.ISSUE, Issue.ACCEPTED_NAME_MISSING);

    // Match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING));
    insert(client, indexName, transfer.toEsDocument(nuw1));

    // Match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCORDING_TO_DATE_INVALID));
    insert(client, indexName, transfer.toEsDocument(nuw2));

    // Match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName, transfer.toEsDocument(nuw3));

    // No match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(null);
    insert(client, indexName, transfer.toEsDocument(nuw4));

    // No match
    NameUsageWrapper nuw5 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw5.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName, transfer.toEsDocument(nuw5));

    // No match
    NameUsageWrapper nuw6 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw6.setIssues(EnumSet.of(Issue.CITATION_UNPARSED, Issue.BASIONYM_ID_INVALID));
    insert(client, indexName, transfer.toEsDocument(nuw6));

    // No match
    NameUsageWrapper nuw7 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw7.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName, transfer.toEsDocument(nuw7));

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
    insert(client, indexName, transfer.toEsDocument(nuw1));

    // Match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCORDING_TO_DATE_INVALID));
    insert(client, indexName, transfer.toEsDocument(nuw2));

    // Match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName, transfer.toEsDocument(nuw3));

    // No match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(null);
    insert(client, indexName, transfer.toEsDocument(nuw4));

    // No match
    NameUsageWrapper nuw5 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw5.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName, transfer.toEsDocument(nuw5));

    // No match
    NameUsageWrapper nuw6 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw6.setIssues(EnumSet.of(Issue.CITATION_UNPARSED, Issue.BASIONYM_ID_INVALID));
    insert(client, indexName, transfer.toEsDocument(nuw6));

    // No match
    NameUsageWrapper nuw7 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw7.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName, transfer.toEsDocument(nuw7));

    // No match
    NameUsageWrapper nuw8 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw8.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.DOUBTFUL_NAME));
    insert(client, indexName, transfer.toEsDocument(nuw8));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(4, result.getResult().size());
  }

  @Test
  public void testQuery3() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Find all documents with an issue of any of ACCEPTED_NAME_MISSING, ACCORDING_TO_DATE_INVALID, BASIONYM_ID_INVALID
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFilter(NameSearchParameter.ISSUE,
        Issue.ACCEPTED_NAME_MISSING, Issue.ACCORDING_TO_DATE_INVALID, Issue.BASIONYM_ID_INVALID);

    // Match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING));
    insert(client, indexName, transfer.toEsDocument(nuw1));

    // Match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCORDING_TO_DATE_INVALID));
    insert(client, indexName, transfer.toEsDocument(nuw2));

    // Match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName, transfer.toEsDocument(nuw3));

    // No match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(null);
    insert(client, indexName, transfer.toEsDocument(nuw4));

    // No match
    NameUsageWrapper nuw5 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw5.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName, transfer.toEsDocument(nuw5));

    // Match
    NameUsageWrapper nuw6 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw6.setIssues(EnumSet.of(Issue.CITATION_UNPARSED, Issue.BASIONYM_ID_INVALID));
    insert(client, indexName, transfer.toEsDocument(nuw6));

    // No match
    NameUsageWrapper nuw7 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw7.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName, transfer.toEsDocument(nuw7));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(4, result.getResult().size());
  }

  @Test
  public void autocomplete1() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setQ("UNLIKE");

    // Match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    List<String> vernaculars = Arrays.asList("AN UNLIKELY NAME");
    nuw1.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toEsDocument(nuw1));

    // Match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("ANOTHER NAME", "AN UNLIKELY NAME");
    nuw2.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toEsDocument(nuw2));

    // Match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("YET ANOTHER NAME", "ANOTHER NAME", "AN UNLIKELY NAME");
    nuw3.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toEsDocument(nuw3));

    // Match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("it's unlike capital case");
    nuw4.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toEsDocument(nuw4));

    // No match
    NameUsageWrapper nuw5 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("LIKE IT OR NOT");
    nuw5.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toEsDocument(nuw5));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(4, result.getResult().size());
  }

  @Test
  public void autocomplete2() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    // Only search in authorship field
    nsr.setContent(EnumSet.of(NameSearchRequest.SearchContent.AUTHORSHIP));
    nsr.setQ("UNLIKE");

    // No match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    List<String> vernaculars = Arrays.asList("AN UNLIKELY NAME");
    nuw1.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toEsDocument(nuw1));

    // No match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("ANOTHER NAME", "AN UNLIKELY NAME");
    nuw2.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toEsDocument(nuw2));

    // No match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("YET ANOTHER NAME", "ANOTHER NAME", "AN UNLIKELY NAME");
    nuw3.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toEsDocument(nuw3));

    // No match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("it's unlike capital case");
    nuw4.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toEsDocument(nuw4));

    // No match
    NameUsageWrapper nuw5 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("LIKE IT OR NOT");
    nuw5.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toEsDocument(nuw5));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(0, result.getResult().size());
  }

  @Test
  public void testIsNull() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Define search condition
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFilter(NameSearchParameter.ISSUE, NameSearchRequest.NULL_VALUE);

    // Match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName, transfer.toEsDocument(nuw1));
    // No match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName, transfer.toEsDocument(nuw2));
    // Match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(null);
    insert(client, indexName, transfer.toEsDocument(nuw3));
    // No match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName, transfer.toEsDocument(nuw4));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(2, result.getResult().size());
  }

  @Test
  public void testIsNotNull() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Define search condition
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFilter(NameSearchParameter.ISSUE, NameSearchRequest.NOT_NULL_VALUE);

    // No match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName, transfer.toEsDocument(nuw1));
    // Match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName, transfer.toEsDocument(nuw2));
    // No match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(null);
    insert(client, indexName, transfer.toEsDocument(nuw3));
    // Match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName, transfer.toEsDocument(nuw4));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(2, result.getResult().size());
  }

  @Test
  public void testNameFieldsQuery1() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Find all documents where the uninomial field is not empty
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFilter(NameSearchParameter.FIELD, "uninomial");

    // Match
    Name n = new Name();
    n.setUninomial("laridae");
    BareName bn = new BareName(n);
    NameUsageWrapper nuw1 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw1));

    // Match
    n = new Name();
    n.setUninomial("parus");
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw2));

    // No match
    n = new Name();
    n.setUninomial(null);
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw3 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw3));

    // Match
    n = new Name();
    n.setUninomial("parus");
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw4 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw4));

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
    nsr.addFilter(NameSearchParameter.FIELD, "uninomial", "remarks", "specific_epithet");

    // Match
    Name n = new Name();
    n.setUninomial("laridae");
    BareName bn = new BareName(n);
    NameUsageWrapper nuw1 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw1));

    // Match
    n = new Name();
    n.setUninomial("parus");
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw2));

    // No match
    n = new Name();
    n.setUninomial(null);
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw3 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw3));

    // Match
    n = new Name();
    n.setUninomial("parus");
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw4 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw4));

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
    nsr.addFilter(NameSearchParameter.FIELD, "uninomial", "remarks", "specific_epithet");

    // Match
    Name n = new Name();
    n.setUninomial("laridae");
    BareName bn = new BareName(n);
    NameUsageWrapper nuw1 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw1));

    // Match
    n = new Name();
    n.setUninomial("parus");
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw2));

    // Match
    n = new Name();
    n.setUninomial(null);
    n.setGenus("parus");
    n.setSpecificEpithet("major");
    n.setRemarks("A bird");
    bn = new BareName(n);
    NameUsageWrapper nuw3 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw3));

    // No Match
    n = new Name();
    n.setUninomial(null);
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw4 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw4));

    // Match
    n = new Name();
    n.setUninomial(null);
    n.setGenus("parus");
    n.setRemarks("A bird");
    bn = new BareName(n);
    NameUsageWrapper nuw5 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw5));

    refreshIndex(client, indexName);

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw3, nuw5);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(expected, result.getResult());

  }

  @Test
  public void testMultipleFiltersAndQ() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Find all documents where the uninomial field is not empty
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setQ("larid");
    nsr.setContent(EnumSet.of(SearchContent.SCIENTIFIC_NAME, SearchContent.AUTHORSHIP));
    nsr.addFilter(NameSearchParameter.FIELD, "uninomial");
    nsr.addFilter(NameSearchParameter.RANK, Rank.ORDER, Rank.FAMILY);
    nsr.addFilter(NameSearchParameter.STATUS, TaxonomicStatus.ACCEPTED);

    // Match
    Name n = new Name();
    n.setScientificName("laridae");
    n.setUninomial("laridae");
    n.setRank(Rank.FAMILY);
    Taxon t = new Taxon();
    t.setName(n);
    t.setProvisional(false);
    VernacularName vn = new VernacularName();
    vn.setName("laridae");
    NameUsageWrapper nuw1 = new NameUsageWrapper(t);
    // Present or not shouldn't make a difference because of SearchContent
    nuw1.setVernacularNames(Arrays.asList(vn));
    insert(client, indexName, transfer.toEsDocument(nuw1));

    // Match
    n = new Name();
    n.setScientificName("laridae");
    n.setUninomial("laridae");
    n.setRank(Rank.ORDER);
    t = new Taxon();
    t.setName(n);
    t.setProvisional(false);
    vn = new VernacularName();
    vn.setName("laridae");
    NameUsageWrapper nuw2 = new NameUsageWrapper(t);
    nuw2.setVernacularNames(Arrays.asList(vn));
    insert(client, indexName, transfer.toEsDocument(nuw2));

    // No match
    n = new Name();
    n.setScientificName("xxx"); // XXXXXXXXXXXXX
    n.setUninomial("laridae");
    n.setRank(Rank.FAMILY);
    t = new Taxon();
    t.setName(n);
    t.setProvisional(false);
    vn = new VernacularName();
    vn.setName("laridae");
    NameUsageWrapper nuw3 = new NameUsageWrapper(t);
    nuw3.setVernacularNames(Arrays.asList(vn));
    insert(client, indexName, transfer.toEsDocument(nuw3));

    // No match
    n = new Name();
    n.setScientificName("laridae");
    n.setUninomial(null); // XXXXXXXXXXXXX
    n.setRank(Rank.FAMILY);
    t = new Taxon();
    t.setName(n);
    t.setProvisional(false);
    vn = new VernacularName();
    vn.setName("laridae");
    NameUsageWrapper nuw4 = new NameUsageWrapper(t);
    nuw4.setVernacularNames(Arrays.asList(vn));
    insert(client, indexName, transfer.toEsDocument(nuw4));

    // No match
    n = new Name();
    n.setScientificName("laridae");
    n.setUninomial(null);
    n.setRank(Rank.GENUS); // XXXXXXXXXXXXX
    t = new Taxon();
    t.setName(n);
    t.setProvisional(false);
    vn = new VernacularName();
    vn.setName("laridae");
    NameUsageWrapper nuw5 = new NameUsageWrapper(t);
    nuw5.setVernacularNames(Arrays.asList(vn));
    insert(client, indexName, transfer.toEsDocument(nuw5));

    // No match
    n = new Name();
    n.setScientificName("laridae");
    n.setUninomial("laridae");
    n.setRank(Rank.FAMILY);
    t = new Taxon();
    t.setName(n);
    t.setProvisional(true); // XXXXXXXXXXXXX
    vn = new VernacularName();
    vn.setName("laridae");
    NameUsageWrapper nuw6 = new NameUsageWrapper(t);
    nuw6.setVernacularNames(Arrays.asList(vn));
    insert(client, indexName, transfer.toEsDocument(nuw6));

    refreshIndex(client, indexName);

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(expected, result.getResult());

  }

  @Test
  public void testWithBigQString() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Find all documents where the uninomial field is not empty
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setQ("ABCDEFGHIJKLMNOPQRSTUVW");

    // Match on scientific name
    Name n = new Name();
    n.setScientificName("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
    BareName bn = new BareName(n);
    NameUsageWrapper nuw1 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw1));

    // Match on vernacular name
    n = new Name();
    n.setScientificName("FOO");
    bn = new BareName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(bn);
    VernacularName vn = new VernacularName();
    vn.setName("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
    nuw2.setVernacularNames(Arrays.asList(vn));
    insert(client, indexName, transfer.toEsDocument(nuw2));

    // Match on authorship
    n = new Name();
    n.setScientificName("BAR");
    bn = new BareName(n);
    NameUsageWrapper nuw3 = new NameUsageWrapper(bn);
    // Bypassing dark art of authorship generation here
    EsNameUsage esn = transfer.toEsDocument(nuw3);
    esn.setAuthorship("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
    insert(client, indexName, esn);

    // No match (missing 'W')
    n = new Name();
    n.setScientificName("ABCDEFGHIJKLMNOPQRSTUV");
    bn = new BareName(n);
    NameUsageWrapper nuw4 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw4));

    // No match (missing 'A')
    n = new Name();
    n.setScientificName("BCDEFGHIJKLMNOPQRSTUVW");
    bn = new BareName(n);
    NameUsageWrapper nuw5 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw5));

    refreshIndex(client, indexName);

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw3);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(expected, result.getResult());

  }

  // Issue #207
  @Test
  public void testWithSmthii__1() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Find all documents where the uninomial field is not empty
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setQ("Smithi");

    Name n = new Name();
    n.setScientificName("Smithii");
    BareName bn = new BareName(n);
    NameUsageWrapper nuw1 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw1));

    n = new Name();
    n.setScientificName("Smithi");
    bn = new BareName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw2));

    n = new Name();
    n.setScientificName("SmithiiFooBar");
    bn = new BareName(n);
    NameUsageWrapper nuw3 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw3));

    n = new Name();
    n.setScientificName("SmithiFooBar");
    bn = new BareName(n);
    NameUsageWrapper nuw4 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw4));

    refreshIndex(client, indexName);

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw3, nuw4);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(expected, result.getResult());

  }

  @Test
  public void testWithSmthii__2() throws IOException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Find all documents where the uninomial field is not empty
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setQ("Smithii");

    Name n = new Name();
    n.setScientificName("Smithii");
    BareName bn = new BareName(n);
    NameUsageWrapper nuw1 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw1));

    n = new Name();
    n.setScientificName("Smithi");
    bn = new BareName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw2));

    n = new Name();
    n.setScientificName("SmithiiFooBar");
    bn = new BareName(n);
    NameUsageWrapper nuw3 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw3));

    n = new Name();
    n.setScientificName("SmithiFooBar");
    bn = new BareName(n);
    NameUsageWrapper nuw4 = new NameUsageWrapper(bn);
    insert(client, indexName, transfer.toEsDocument(nuw4));

    refreshIndex(client, indexName);

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw3, nuw4);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(expected, result.getResult());

  }

  private static List<VernacularName> create(List<String> names) {
    return names.stream().map(n -> {
      VernacularName vn = new VernacularName();
      vn.setName(n);
      return vn;
    }).collect(Collectors.toList());
  }
}
