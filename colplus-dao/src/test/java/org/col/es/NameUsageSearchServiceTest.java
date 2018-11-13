package org.col.es;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import org.col.api.TestEntityGenerator;
import org.col.api.model.BareName;
import org.col.api.model.Name;
import org.col.api.model.NameUsage;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.model.Synonym;
import org.col.api.model.Taxon;
import org.col.api.model.VernacularName;
import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchRequest.SortBy;
import org.col.api.search.NameUsageWrapper;
import org.col.api.vocab.Issue;
import org.col.es.model.EsNameUsage;
import org.elasticsearch.client.RestClient;
import org.gbif.nameparser.api.Rank;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
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
  public void testSort1() throws InvalidQueryException {
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
    ResultPage<NameUsageWrapper<NameUsage>> result = svc.search(indexName, nsr, new Page());
    assertEquals(3, result.getResult().size());
    assertEquals(Taxon.class, result.getResult().get(0).getUsage().getClass());
    assertEquals(Synonym.class, result.getResult().get(1).getUsage().getClass());
    assertEquals(BareName.class, result.getResult().get(2).getUsage().getClass());
  }

  @Test
  public void testSort2() throws InvalidQueryException {
    NameUsageTransfer transfer = new NameUsageTransfer();
    EsNameUsage enu = transfer.toEsDocument(TestEntityGenerator.newNameUsageTaxonWrapper());
    // Overwrite to test ordering by scientific name
    enu.setScientificName("B");
    insert(client, indexName, enu);
    enu = transfer.toEsDocument(TestEntityGenerator.newNameUsageSynonymWrapper());
    enu.setScientificName("C");
    insert(client, indexName, enu);
    enu = transfer.toEsDocument(TestEntityGenerator.newNameUsageBareNameWrapper());
    enu.setScientificName("A");
    insert(client, indexName, enu);
    refreshIndex(client, indexName);
    assertEquals(3, EsUtil.count(client, indexName));
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setSortBy(SortBy.NAME);
    ResultPage<NameUsageWrapper<NameUsage>> result = svc.search(indexName, nsr, new Page());
    assertEquals(3, result.getResult().size());
    assertEquals(BareName.class, result.getResult().get(0).getUsage().getClass());
    assertEquals(Taxon.class, result.getResult().get(1).getUsage().getClass());
    assertEquals(Synonym.class, result.getResult().get(2).getUsage().getClass());
  }

  @Test
  public void testSortTaxonomic() throws InvalidQueryException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setSortBy(SortBy.TAXONOMIC);

    // Don't forget this one; we're going to insert more than 10 docs
    Page page = new Page(100);

    Name n = new Name();
    n.setRank(Rank.KINGDOM);
    n.setScientificName("B");
    Taxon t = new Taxon();
    t.setName(n);
    final NameUsageWrapper<Taxon> nuw1 = new NameUsageWrapper<>(t);

    n = new Name();
    n.setRank(Rank.KINGDOM);
    n.setScientificName("Z");
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper<Taxon> nuw2 = new NameUsageWrapper<>(t);

    n = new Name();
    n.setRank(Rank.PHYLUM);
    n.setScientificName("E");
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper<Taxon> nuw3 = new NameUsageWrapper<>(t);

    n = new Name();
    n.setRank(Rank.PHYLUM);
    n.setScientificName("C");
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper<Taxon> nuw4 = new NameUsageWrapper<>(t);

    n = new Name();
    n.setRank(Rank.CLASS);
    n.setScientificName("Y");
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper<Taxon> nuw5 = new NameUsageWrapper<>(t);

    n = new Name();
    n.setRank(Rank.ORDER);
    n.setScientificName("X");
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper<Taxon> nuw6 = new NameUsageWrapper<>(t);

    n = new Name();
    n.setRank(Rank.FAMILY);
    n.setScientificName("V");
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper<Taxon> nuw7 = new NameUsageWrapper<>(t);

    n = new Name();
    n.setRank(Rank.GENUS);
    n.setScientificName("Q");
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper<Taxon> nuw8 = new NameUsageWrapper<>(t);

    n = new Name();
    n.setRank(Rank.SPECIES);
    n.setScientificName("M");
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper<Taxon> nuw9 = new NameUsageWrapper<>(t);

    n = new Name();
    n.setRank(Rank.SPECIES);
    n.setScientificName("L");
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper<Taxon> nuw10 = new NameUsageWrapper<>(t);

    n = new Name();
    n.setRank(Rank.SPECIES);
    n.setScientificName("K");
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper<Taxon> nuw11 = new NameUsageWrapper<>(t);

    List<NameUsageWrapper<Taxon>> all = Arrays.asList(nuw1, nuw2, nuw3, nuw4, nuw5, nuw6, nuw7, nuw8, nuw9, nuw10, nuw11);
    List<NameUsageWrapper<Taxon>> shuffled = new ArrayList<>(all);
    Collections.shuffle(shuffled);
    shuffled.stream().map(transfer::toEsDocument).forEach(x -> insert(client, indexName, x));
    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper<NameUsage>> result = svc.search(indexName, nsr, page);

    assertEquals(all.size(), result.getResult().size());

    // This will currently not work because of empty lists/sets in the in-going name usages having been converted to null in the out-going
    // name usages.
    // assertEquals(all, result.getResult());

  }

  @Test
  public void testQuery1() throws InvalidQueryException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFilter(NameSearchParameter.ISSUE, Issue.ACCEPTED_NAME_MISSING);

    // Match
    NameUsageWrapper<Taxon> nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING));
    insert(client, indexName, transfer.toEsDocument(nuw1));

    // Match
    NameUsageWrapper<Taxon> nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCORDING_TO_DATE_INVALID));
    insert(client, indexName, transfer.toEsDocument(nuw2));

    // Match
    NameUsageWrapper<Taxon> nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName, transfer.toEsDocument(nuw3));

    // No match
    NameUsageWrapper<Taxon> nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(null);
    insert(client, indexName, transfer.toEsDocument(nuw4));

    // No match
    NameUsageWrapper<Taxon> nuw5 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw5.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName, transfer.toEsDocument(nuw5));

    // No match
    NameUsageWrapper<Taxon> nuw6 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw6.setIssues(EnumSet.of(Issue.CITATION_UNPARSED, Issue.BASIONYM_ID_INVALID));
    insert(client, indexName, transfer.toEsDocument(nuw6));

    // No match
    NameUsageWrapper<Taxon> nuw7 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw7.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName, transfer.toEsDocument(nuw7));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper<NameUsage>> result = svc.search(indexName, nsr, new Page());

    assertEquals(3, result.getResult().size());
  }

  @Test
  @Ignore
  public void testQuery2() throws InvalidQueryException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFilter(NameSearchParameter.ISSUE, EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCORDING_TO_DATE_INVALID));

    // Match
    NameUsageWrapper<Taxon> nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING));
    insert(client, indexName, transfer.toEsDocument(nuw1));

    // Match
    NameUsageWrapper<Taxon> nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCORDING_TO_DATE_INVALID));
    insert(client, indexName, transfer.toEsDocument(nuw2));

    // Match
    NameUsageWrapper<Taxon> nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName, transfer.toEsDocument(nuw3));

    // No match
    NameUsageWrapper<Taxon> nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(null);
    insert(client, indexName, transfer.toEsDocument(nuw4));

    // No match
    NameUsageWrapper<Taxon> nuw5 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw5.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName, transfer.toEsDocument(nuw5));

    // No match
    NameUsageWrapper<Taxon> nuw6 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw6.setIssues(EnumSet.of(Issue.CITATION_UNPARSED, Issue.BASIONYM_ID_INVALID));
    insert(client, indexName, transfer.toEsDocument(nuw6));

    // No match
    NameUsageWrapper<Taxon> nuw7 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw7.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName, transfer.toEsDocument(nuw7));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper<NameUsage>> result = svc.search(indexName, nsr, new Page());

    assertEquals(3, result.getResult().size());
  }

  @Test
  @Ignore
  public void testQuery3() throws InvalidQueryException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Define search condition
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFilter(NameSearchParameter.ISSUE,
        new Issue[] {Issue.ACCEPTED_NAME_MISSING, Issue.ACCORDING_TO_DATE_INVALID, Issue.BASIONYM_ID_INVALID});

    // Match
    NameUsageWrapper<Taxon> nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING));
    insert(client, indexName, transfer.toEsDocument(nuw1));

    // Match
    NameUsageWrapper<Taxon> nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCORDING_TO_DATE_INVALID));
    insert(client, indexName, transfer.toEsDocument(nuw2));

    // Match
    NameUsageWrapper<Taxon> nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName, transfer.toEsDocument(nuw3));

    // No match
    NameUsageWrapper<Taxon> nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(null);
    insert(client, indexName, transfer.toEsDocument(nuw4));

    // No match
    NameUsageWrapper<Taxon> nuw5 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw5.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName, transfer.toEsDocument(nuw5));

    // Match
    NameUsageWrapper<Taxon> nuw6 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw6.setIssues(EnumSet.of(Issue.CITATION_UNPARSED, Issue.BASIONYM_ID_INVALID));
    insert(client, indexName, transfer.toEsDocument(nuw6));

    // No match
    NameUsageWrapper<Taxon> nuw7 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw7.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName, transfer.toEsDocument(nuw7));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper<NameUsage>> result = svc.search(indexName, nsr, new Page());

    assertEquals(4, result.getResult().size());
  }

  @Test
  public void autocomplete1() throws InvalidQueryException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setQ("UNLIKE");

    // Match
    NameUsageWrapper<Taxon> nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    List<String> vernaculars = Arrays.asList("AN UNLIKELY NAME");
    nuw1.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toEsDocument(nuw1));

    // Match
    NameUsageWrapper<Taxon> nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("ANOTHER NAME", "AN UNLIKELY NAME");
    nuw2.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toEsDocument(nuw2));

    // Match
    NameUsageWrapper<Taxon> nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("YET ANOTHER NAME", "ANOTHER NAME", "AN UNLIKELY NAME");
    nuw3.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toEsDocument(nuw3));

    // Match
    NameUsageWrapper<Taxon> nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("it's unlike capital case");
    nuw4.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toEsDocument(nuw4));

    // No match
    NameUsageWrapper<Taxon> nuw5 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("LIKE IT OR NOT");
    nuw5.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toEsDocument(nuw5));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper<NameUsage>> result = svc.search(indexName, nsr, new Page());

    assertEquals(4, result.getResult().size());
  }

  @Test
  public void autocomplete2() throws InvalidQueryException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    // Only search in authorship field
    nsr.setContent(EnumSet.of(NameSearchRequest.SearchContent.AUTHORSHIP));
    nsr.setQ("UNLIKE");

    // No match
    NameUsageWrapper<Taxon> nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    List<String> vernaculars = Arrays.asList("AN UNLIKELY NAME");
    nuw1.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toEsDocument(nuw1));

    // No match
    NameUsageWrapper<Taxon> nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("ANOTHER NAME", "AN UNLIKELY NAME");
    nuw2.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toEsDocument(nuw2));

    // No match
    NameUsageWrapper<Taxon> nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("YET ANOTHER NAME", "ANOTHER NAME", "AN UNLIKELY NAME");
    nuw3.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toEsDocument(nuw3));

    // No match
    NameUsageWrapper<Taxon> nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("it's unlike capital case");
    nuw4.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toEsDocument(nuw4));

    // No match
    NameUsageWrapper<Taxon> nuw5 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("LIKE IT OR NOT");
    nuw5.setVernacularNames(create(vernaculars));
    insert(client, indexName, transfer.toEsDocument(nuw5));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper<NameUsage>> result = svc.search(indexName, nsr, new Page());

    assertEquals(0, result.getResult().size());
  }

  @Test
  public void testIsNull() throws InvalidQueryException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Define search condition
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFilter(NameSearchParameter.ISSUE, NameSearchRequest.NULL_VALUE);

    // Match
    NameUsageWrapper<Taxon> nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName, transfer.toEsDocument(nuw1));
    // No match
    NameUsageWrapper<Taxon> nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName, transfer.toEsDocument(nuw2));
    // Match
    NameUsageWrapper<Taxon> nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(null);
    insert(client, indexName, transfer.toEsDocument(nuw3));
    // No match
    NameUsageWrapper<Taxon> nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName, transfer.toEsDocument(nuw4));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper<NameUsage>> result = svc.search(indexName, nsr, new Page());

    assertEquals(2, result.getResult().size());
  }

  @Test
  public void testIsNotNull() throws InvalidQueryException {
    NameUsageTransfer transfer = new NameUsageTransfer();

    // Define search condition
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFilter(NameSearchParameter.ISSUE, NameSearchRequest.NOT_NULL_VALUE);

    // No match
    NameUsageWrapper<Taxon> nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName, transfer.toEsDocument(nuw1));
    // Match
    NameUsageWrapper<Taxon> nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName, transfer.toEsDocument(nuw2));
    // No match
    NameUsageWrapper<Taxon> nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(null);
    insert(client, indexName, transfer.toEsDocument(nuw3));
    // Match
    NameUsageWrapper<Taxon> nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName, transfer.toEsDocument(nuw4));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper<NameUsage>> result = svc.search(indexName, nsr, new Page());

    assertEquals(2, result.getResult().size());
  }

  public void testFieldsQuery() {
    NameUsageTransfer transfer = new NameUsageTransfer();

    Name n = new Name();
    n.setUninomial("laridae");
    BareName bn = new BareName(n);
    NameUsageWrapper<BareName> nuw = new NameUsageWrapper<BareName>(bn);
    EsNameUsage doc = transfer.toEsDocument(nuw);
    insert(client, indexName, doc);

    n = new Name();
    n.setUninomial("parus");
    n.setGenus("parus");
    bn = new BareName(n);
    nuw = new NameUsageWrapper<BareName>(bn);
    doc = transfer.toEsDocument(nuw);
    insert(client, indexName, doc);

    n = new Name();
    n.setUninomial("parus");
    n.setGenus("parus");
    bn = new BareName(n);
    nuw = new NameUsageWrapper<BareName>(bn);
    doc = transfer.toEsDocument(nuw);
    insert(client, indexName, doc);
  }

  private static List<VernacularName> create(List<String> names) {
    return names.stream().map(n -> {
      VernacularName vn = new VernacularName();
      vn.setName(n);
      return vn;
    }).collect(Collectors.toList());
  }
}
