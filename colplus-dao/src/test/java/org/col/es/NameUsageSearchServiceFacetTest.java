package org.col.es;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.zip.DeflaterOutputStream;

import org.col.api.TestEntityGenerator;
import org.col.api.model.Name;
import org.col.api.model.NameUsage;
import org.col.api.model.Page;
import org.col.api.model.Taxon;
import org.col.api.search.FacetValue;
import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchResponse;
import org.col.api.search.NameUsageWrapper;
import org.col.api.vocab.Issue;
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

public class NameUsageSearchServiceFacetTest extends EsReadTestBase {

  private static final String dummyPayload = getDummyPayload();

  private static RestClient client;
  private static NameUsageSearchService svc;

  @BeforeClass
  public static void init() {
    client = esSetupRule.getEsClient();
    svc = new NameUsageSearchService(indexName, esSetupRule.getEsClient());
  }

  @AfterClass
  public static void shutdown() throws IOException {
    client.close();
  }

  @Before
  public void before() throws IOException {
    EsUtil.deleteIndex(client, indexName);
    EsUtil.createIndex(client, indexName, getEsConfig().nameUsage);
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
  public void testSingleFacetWithoutFilters() throws IOException {
    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFacet(NameSearchParameter.RANK);
    // Don't forget this one; we're going to insert more than 10 docs
    Page page = new Page(100);

    // 4 kingdoms
    EsNameUsage enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setScientificNameWN("Animalia");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setScientificNameWN("Plantae");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setScientificNameWN("Fungi");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setScientificNameWN("Bacteria");
    insert(client, indexName, enu);

    // 4 Phylae
    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setScientificNameWN("Arthropoda");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setScientificNameWN("Brachiopoda");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setScientificNameWN("Chordata");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setScientificNameWN("Mollusca");
    insert(client, indexName, enu);

    // 2 classes
    enu = newEsNameUsage();
    enu.setRank(Rank.CLASS);
    enu.setScientificNameWN("Mammalia");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.CLASS);
    enu.setScientificNameWN("Aves");
    insert(client, indexName, enu);

    // Zero orders & families

    // 3 genera
    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setScientificNameWN("Homo");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setScientificNameWN("Larus");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setScientificNameWN("Abies");
    insert(client, indexName, enu);

    // 1 species
    enu = newEsNameUsage();
    enu.setRank(Rank.SPECIES);
    enu.setScientificNameWN("Larus fuscus");
    insert(client, indexName, enu);

    refreshIndex(client, indexName);

    NameSearchResponse result = svc.search(indexName, nsr, page);

    Map<NameSearchParameter, Set<FacetValue<?>>> expected = new HashMap<>();
    Set<FacetValue<?>> rankFacet = new TreeSet<>();
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.KINGDOM.ordinal(), 4));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.PHYLUM.ordinal(), 4));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.GENUS.ordinal(), 3));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.CLASS.ordinal(), 2));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.SPECIES.ordinal(), 1));
    expected.put(NameSearchParameter.RANK, rankFacet);

    assertEquals(expected, result.getFacets());

  }

  @Test
  public void testTwoFacetsWithoutFilters() throws IOException {
    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFacet(NameSearchParameter.RANK);
    nsr.addFacet(NameSearchParameter.ISSUE);
    Page page = new Page(100);

    EsNameUsage enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCEPTED_ID_INVALID));
    enu.setScientificNameWN("Animalia");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.BASIONYM_ID_INVALID));
    enu.setScientificNameWN("Plantae");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.BASIONYM_ID_INVALID));
    enu.setScientificNameWN("Fungi");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.BASIONYM_ID_INVALID));
    enu.setScientificNameWN("Bacteria");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING));
    enu.setScientificNameWN("Arthropoda");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.CITATION_UNPARSED));
    enu.setScientificNameWN("Brachiopoda");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCEPTED_ID_INVALID, Issue.CLASSIFICATION_NOT_APPLIED));
    enu.setScientificNameWN("Chordata");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setScientificNameWN("Mollusca");
    insert(client, indexName, enu);

    // 2 classes
    enu = newEsNameUsage();
    enu.setRank(Rank.CLASS);
    enu.setScientificNameWN("Mammalia");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.CLASS);
    enu.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    enu.setScientificNameWN("Aves");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setScientificNameWN("Homo");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setScientificNameWN("Larus");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setScientificNameWN("Abies");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.SPECIES);
    enu.setScientificNameWN("Larus fuscus");
    insert(client, indexName, enu);

    // ACCEPTED_NAME_MISSING: 5 docs
    // BASIONYM_ID_INVALID: 3 docs
    // ACCEPTED_ID_INVALID: 2 docs
    // CITATION_UNPARSED: 2 docs
    // CLASSIFICATION_NOT_APPLIED: 1 doc

    refreshIndex(client, indexName);

    NameSearchResponse result = svc.search(indexName, nsr, page);

    Map<NameSearchParameter, Set<FacetValue<?>>> expected = new HashMap<>();

    Set<FacetValue<?>> rankFacet = new TreeSet<>();
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.KINGDOM.ordinal(), 4));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.PHYLUM.ordinal(), 4));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.GENUS.ordinal(), 3));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.CLASS.ordinal(), 2));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.SPECIES.ordinal(), 1));
    expected.put(NameSearchParameter.RANK, rankFacet);

    Set<FacetValue<?>> issueFacet = new TreeSet<>();
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.ACCEPTED_NAME_MISSING.ordinal(), 5));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.BASIONYM_ID_INVALID.ordinal(), 3));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.ACCEPTED_ID_INVALID.ordinal(), 2));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.CITATION_UNPARSED.ordinal(), 2));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.CLASSIFICATION_NOT_APPLIED.ordinal(), 1));
    expected.put(NameSearchParameter.ISSUE, issueFacet);

    assertEquals(expected, result.getFacets());

  }

  @Test
  public void testThreeFacetsWithoutFilters() throws IOException {
    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFacet(NameSearchParameter.RANK);
    nsr.addFacet(NameSearchParameter.ISSUE);
    nsr.addFacet(NameSearchParameter.PUBLISHED_IN_ID);
    Page page = new Page(100);

    String PUB_ID1 = "PUB_0001";
    String PUB_ID2 = "PUB_0002";

    // 4 kingdoms
    EsNameUsage enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCEPTED_ID_INVALID));
    enu.setScientificNameWN("Animalia");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.BASIONYM_ID_INVALID));
    enu.setPublishedInId(PUB_ID1);
    enu.setScientificNameWN("Plantae");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.BASIONYM_ID_INVALID));
    enu.setPublishedInId(PUB_ID1);
    enu.setScientificNameWN("Fungi");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.BASIONYM_ID_INVALID));
    enu.setPublishedInId(PUB_ID1);
    enu.setScientificNameWN("Bacteria");
    insert(client, indexName, enu);

    // 4 Phylae
    enu = newEsNameUsage();
    enu.setPublishedInId(PUB_ID1);
    enu.setRank(Rank.PHYLUM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING));
    enu.setScientificNameWN("Arthropoda");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.CITATION_UNPARSED));
    enu.setPublishedInId(PUB_ID2);
    enu.setScientificNameWN("Brachiopoda");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCEPTED_ID_INVALID, Issue.CLASSIFICATION_NOT_APPLIED));
    enu.setPublishedInId(PUB_ID2);
    enu.setScientificNameWN("Chordata");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setScientificNameWN("Mollusca");
    insert(client, indexName, enu);

    // 2 classes
    enu = newEsNameUsage();
    enu.setRank(Rank.CLASS);
    enu.setPublishedInId(PUB_ID2);
    enu.setScientificNameWN("Mammalia");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.CLASS);
    enu.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    enu.setScientificNameWN("Aves");
    insert(client, indexName, enu);

    // Zero orders & families

    // 3 genera
    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setPublishedInId(PUB_ID1);
    enu.setScientificNameWN("Homo");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setPublishedInId(PUB_ID1);
    enu.setScientificNameWN("Larus");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setScientificNameWN("Abies");
    insert(client, indexName, enu);

    // 1 species
    enu = newEsNameUsage();
    enu.setRank(Rank.SPECIES);
    enu.setPublishedInId(PUB_ID2);
    enu.setScientificNameWN("Larus fuscus");
    insert(client, indexName, enu);

    // ACCEPTED_NAME_MISSING: 5 docs
    // BASIONYM_ID_INVALID: 3 docs
    // ACCEPTED_ID_INVALID: 2 docs
    // CITATION_UNPARSED: 2 docs
    // CLASSIFICATION_NOT_APPLIED: 1 doc

    // PUB_ID1: 6 docs
    // PUB_ID2: 4 docs

    refreshIndex(client, indexName);

    NameSearchResponse result = svc.search(indexName, nsr, page);

    Map<NameSearchParameter, Set<FacetValue<?>>> expected = new HashMap<>();

    Set<FacetValue<?>> rankFacet = new TreeSet<>();
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.KINGDOM.ordinal(), 4)); // Descending doc count !!!
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.PHYLUM.ordinal(), 4));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.GENUS.ordinal(), 3));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.CLASS.ordinal(), 2));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.SPECIES.ordinal(), 1));
    expected.put(NameSearchParameter.RANK, rankFacet);

    Set<FacetValue<?>> issueFacet = new TreeSet<>();
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.ACCEPTED_NAME_MISSING.ordinal(), 5));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.BASIONYM_ID_INVALID.ordinal(), 3));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.ACCEPTED_ID_INVALID.ordinal(), 2));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.CITATION_UNPARSED.ordinal(), 2));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.CLASSIFICATION_NOT_APPLIED.ordinal(), 1));
    expected.put(NameSearchParameter.ISSUE, issueFacet);

    Set<FacetValue<?>> pubIdFacet = new TreeSet<>();
    pubIdFacet.add(FacetValue.forString(PUB_ID1, 6));
    pubIdFacet.add(FacetValue.forString(PUB_ID2, 4));
    expected.put(NameSearchParameter.PUBLISHED_IN_ID, pubIdFacet);

    assertEquals(expected, result.getFacets());

  }

  @Test
  public void testThreeFacetsWithQ() throws InvalidQueryException, IOException {
    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFacet(NameSearchParameter.RANK);
    nsr.addFacet(NameSearchParameter.ISSUE);
    nsr.addFacet(NameSearchParameter.PUBLISHED_IN_ID);
    nsr.setQ("BBBB");
    Page page = new Page(100);

    String PUB_ID1 = "PUB_0001";
    String PUB_ID2 = "PUB_0002";

    EsNameUsage enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCEPTED_ID_INVALID));
    enu.setScientificNameWN("AAAAA");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.BASIONYM_ID_INVALID));
    enu.setPublishedInId(PUB_ID1);
    enu.setScientificNameWN("BBBBB");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.BASIONYM_ID_INVALID));
    enu.setPublishedInId(PUB_ID1);
    enu.setScientificNameWN("AAAAA");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.BASIONYM_ID_INVALID));
    enu.setPublishedInId(PUB_ID1);
    enu.setScientificNameWN("BBBBB");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setPublishedInId(PUB_ID1);
    enu.setRank(Rank.PHYLUM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING));
    enu.setScientificNameWN("AAAAA");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.CITATION_UNPARSED));
    enu.setPublishedInId(PUB_ID2);
    enu.setScientificNameWN("BBBBB");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCEPTED_ID_INVALID, Issue.CLASSIFICATION_NOT_APPLIED));
    enu.setPublishedInId(PUB_ID2);
    enu.setScientificNameWN("AAAAA");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setScientificNameWN("BBBBB");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.CLASS);
    enu.setPublishedInId(PUB_ID2);
    enu.setScientificNameWN("AAAAA");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.CLASS);
    enu.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    enu.setScientificNameWN("BBBBB");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setPublishedInId(PUB_ID1);
    enu.setScientificNameWN("AAAAA");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setPublishedInId(PUB_ID1);
    enu.setScientificNameWN("BBBBB");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setScientificNameWN("AAAAA");
    insert(client, indexName, enu);

    // 1 species
    enu = newEsNameUsage();
    enu.setRank(Rank.SPECIES);
    enu.setPublishedInId(PUB_ID2);
    enu.setScientificNameWN("BBBBB");
    insert(client, indexName, enu);

    // Q = "BBBB";

    // KINGDOM: 2 docs
    // PHYLUM: 2 docs
    // CLASS: 1 doc
    // GENUS: 1 doc
    // SPECIES: 1 doc

    // ACCEPTED_NAME_MISSING: 2 docs
    // BASIONYM_ID_INVALID: 2 docs
    // ACCEPTED_ID_INVALID: 0 docs
    // CITATION_UNPARSED: 2 docs
    // CLASSIFICATION_NOT_APPLIED: 0 docs

    // PUB_ID1: 3 docs
    // PUB_ID2: 2 docs

    refreshIndex(client, indexName);

    NameSearchResponse result = svc.search(indexName, nsr, page);

    Map<NameSearchParameter, Set<FacetValue<?>>> expected = new HashMap<>();

    Set<FacetValue<?>> rankFacet = new TreeSet<>();
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.KINGDOM.ordinal(), 2));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.PHYLUM.ordinal(), 2));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.CLASS.ordinal(), 1));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.GENUS.ordinal(), 1));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.SPECIES.ordinal(), 1));
    expected.put(NameSearchParameter.RANK, rankFacet);

    Set<FacetValue<?>> issueFacet = new TreeSet<>();
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.ACCEPTED_NAME_MISSING.ordinal(), 2));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.BASIONYM_ID_INVALID.ordinal(), 2));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.CITATION_UNPARSED.ordinal(), 2));
    expected.put(NameSearchParameter.ISSUE, issueFacet);

    Set<FacetValue<?>> pubIdFacet = new TreeSet<>();
    pubIdFacet.add(FacetValue.forString(PUB_ID1, 3));
    pubIdFacet.add(FacetValue.forString(PUB_ID2, 2));
    expected.put(NameSearchParameter.PUBLISHED_IN_ID, pubIdFacet);

    assertEquals(expected, result.getFacets());

  }

  @Test
  public void testThreeFacetsWithOneFacetFilter() throws IOException {

    String PUB_ID1 = "PUB_0001";
    String PUB_ID2 = "PUB_0002";

    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFacet(NameSearchParameter.RANK);
    nsr.addFacet(NameSearchParameter.ISSUE);
    nsr.addFacet(NameSearchParameter.PUBLISHED_IN_ID);
    nsr.addFilter(NameSearchParameter.PUBLISHED_IN_ID, PUB_ID1);
    Page page = new Page(100);

    EsNameUsage enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCEPTED_ID_INVALID));
    enu.setScientificNameWN("AAAAA");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.BASIONYM_ID_INVALID));
    enu.setPublishedInId(PUB_ID1);
    enu.setScientificNameWN("BBBBB");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.BASIONYM_ID_INVALID));
    enu.setPublishedInId(PUB_ID1);
    enu.setScientificNameWN("AAAAA");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.BASIONYM_ID_INVALID));
    enu.setPublishedInId(PUB_ID1);
    enu.setScientificNameWN("BBBBB");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setPublishedInId(PUB_ID1);
    enu.setRank(Rank.PHYLUM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING));
    enu.setScientificNameWN("AAAAA");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.CITATION_UNPARSED));
    enu.setPublishedInId(PUB_ID2);
    enu.setScientificNameWN("BBBBB");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCEPTED_ID_INVALID, Issue.CLASSIFICATION_NOT_APPLIED));
    enu.setPublishedInId(PUB_ID2);
    enu.setScientificNameWN("AAAAA");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setScientificNameWN("BBBBB");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.CLASS);
    enu.setPublishedInId(PUB_ID2);
    enu.setScientificNameWN("AAAAA");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.CLASS);
    enu.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    enu.setScientificNameWN("BBBBB");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setPublishedInId(PUB_ID1);
    enu.setScientificNameWN("AAAAA");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setPublishedInId(PUB_ID1);
    enu.setScientificNameWN("BBBBB");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setScientificNameWN("AAAAA");
    insert(client, indexName, enu);

    // 1 species
    enu = newEsNameUsage();
    enu.setRank(Rank.SPECIES);
    enu.setPublishedInId(PUB_ID2);
    enu.setScientificNameWN("BBBBB");
    insert(client, indexName, enu);

    // NAME_PUBLISHED_IN_ID = "PUB_0001"

    // KINGDOM: 3 docs
    // PHYLUM: 1 docs
    // CLASS: 0 docs
    // GENUS: 2 doc
    // SPECIES: 0 docs

    // ACCEPTED_NAME_MISSING: 2 docs
    // BASIONYM_ID_INVALID: 3 docs
    // ACCEPTED_ID_INVALID: 0 docs
    // CITATION_UNPARSED: 0 docs
    // CLASSIFICATION_NOT_APPLIED: 0 docs

    // PUB_ID1: 6 docs (SHOULD NOT BE AFFECTED BY FILTER !)
    // PUB_ID2: 4 docs

    refreshIndex(client, indexName);

    NameSearchResponse result = svc.search(indexName, nsr, page);

    Map<NameSearchParameter, Set<FacetValue<?>>> expected = new HashMap<>();

    Set<FacetValue<?>> rankFacet = new TreeSet<>();
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.KINGDOM.ordinal(), 3));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.PHYLUM.ordinal(), 1));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.GENUS.ordinal(), 2));
    expected.put(NameSearchParameter.RANK, rankFacet);

    Set<FacetValue<?>> issueFacet = new TreeSet<>();
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.ACCEPTED_NAME_MISSING.ordinal(), 2));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.BASIONYM_ID_INVALID.ordinal(), 3));
    expected.put(NameSearchParameter.ISSUE, issueFacet);

    Set<FacetValue<?>> pubIdFacet = new TreeSet<>();
    pubIdFacet.add(FacetValue.forString(PUB_ID1, 6));
    pubIdFacet.add(FacetValue.forString(PUB_ID2, 4));
    expected.put(NameSearchParameter.PUBLISHED_IN_ID, pubIdFacet);

    assertEquals(expected, result.getFacets());

  }

  @Test // Make sure UUID facets work OK
  public void testPublisherKey() throws IOException {
    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFacet(NameSearchParameter.PUBLISHER_KEY);
    Page page = new Page(100);

    UUID uuid1 = UUID.randomUUID();
    UUID uuid2 = UUID.randomUUID();

    // UUID1
    NameUsageWrapper nuw1 = minimalNameUsage();
    nuw1.setPublisherKey(uuid1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    nuw2.setPublisherKey(uuid1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    nuw3.setPublisherKey(uuid1);

    // UUID2
    NameUsageWrapper nuw4 = minimalNameUsage();
    nuw4.setPublisherKey(uuid2);
    NameUsageWrapper nuw5 = minimalNameUsage();
    nuw5.setPublisherKey(uuid2);

    // NO UUID
    NameUsageWrapper nuw6 = minimalNameUsage();
    nuw6.setPublisherKey(null);
    NameUsageWrapper nuw7 = minimalNameUsage();
    nuw7.setPublisherKey(null);

    NameUsageTransfer transfer = new NameUsageTransfer();

    insert(client, indexName, transfer.toDocument(nuw1));
    insert(client, indexName, transfer.toDocument(nuw2));
    insert(client, indexName, transfer.toDocument(nuw3));
    insert(client, indexName, transfer.toDocument(nuw4));
    insert(client, indexName, transfer.toDocument(nuw5));
    insert(client, indexName, transfer.toDocument(nuw6));
    insert(client, indexName, transfer.toDocument(nuw7));
    refreshIndex(client, indexName);

    // Resurrect NameUsageWrapper instances b/c they got pruned upon insert.
    nuw1.setPublisherKey(uuid1);
    nuw2.setPublisherKey(uuid1);
    nuw3.setPublisherKey(uuid1);
    nuw4.setPublisherKey(uuid2);
    nuw5.setPublisherKey(uuid2);
    nuw6.setPublisherKey(null);
    nuw7.setPublisherKey(null);

    Map<NameSearchParameter, Set<FacetValue<?>>> expected = new HashMap<>();
    Set<FacetValue<?>> pkFacet = new TreeSet<>();
    pkFacet.add(FacetValue.forUuid(uuid1, 3));
    pkFacet.add(FacetValue.forUuid(uuid2, 2));
    expected.put(NameSearchParameter.PUBLISHER_KEY, pkFacet);

    NameSearchResponse result = svc.search(indexName, nsr, page);

    assertEquals(expected, result.getFacets());

  }

  @Test
  public void testSectorKey() throws IOException {
    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFacet(NameSearchParameter.SECTOR_KEY);
    Page page = new Page(100);

    Integer key1 = 1000;
    Integer key2 = 2000;

    // key1
    NameUsageWrapper nuw1 = minimalNameUsage();
    ((Taxon) nuw1.getUsage()).setSectorKey(key1);
    NameUsageWrapper nuw2 = minimalNameUsage();
    ((Taxon) nuw2.getUsage()).setSectorKey(key1);
    NameUsageWrapper nuw3 = minimalNameUsage();
    ((Taxon) nuw3.getUsage()).setSectorKey(key1);

    // key2
    NameUsageWrapper nuw4 = minimalNameUsage();
    ((Taxon) nuw4.getUsage()).setSectorKey(key2);
    NameUsageWrapper nuw5 = minimalNameUsage();
    ((Taxon) nuw5.getUsage()).setSectorKey(key2);

    // no sector key
    NameUsageWrapper nuw6 = minimalNameUsage();
    ((Taxon) nuw6.getUsage()).setSectorKey(null);
    NameUsageWrapper nuw7 = minimalNameUsage();
    ((Taxon) nuw7.getUsage()).setSectorKey(null);

    NameUsageTransfer transfer = new NameUsageTransfer();

    insert(client, indexName, transfer.toDocument(nuw1));
    insert(client, indexName, transfer.toDocument(nuw2));
    insert(client, indexName, transfer.toDocument(nuw3));
    insert(client, indexName, transfer.toDocument(nuw4));
    insert(client, indexName, transfer.toDocument(nuw5));
    insert(client, indexName, transfer.toDocument(nuw6));
    insert(client, indexName, transfer.toDocument(nuw7));
    refreshIndex(client, indexName);

    // Resurrect NameUsageWrapper instances b/c they got pruned upon insert.
    ((Taxon) nuw1.getUsage()).setSectorKey(key1);
    ((Taxon) nuw2.getUsage()).setSectorKey(key1);
    ((Taxon) nuw3.getUsage()).setSectorKey(key1);
    ((Taxon) nuw4.getUsage()).setSectorKey(key2);
    ((Taxon) nuw5.getUsage()).setSectorKey(key2);
    ((Taxon) nuw6.getUsage()).setSectorKey(null);
    ((Taxon) nuw7.getUsage()).setSectorKey(null);

    Map<NameSearchParameter, Set<FacetValue<?>>> expected = new HashMap<>();
    Set<FacetValue<?>> skFacet = new TreeSet<>();
    skFacet.add(FacetValue.forInteger(key1, 3));
    skFacet.add(FacetValue.forInteger(key2, 2));
    expected.put(NameSearchParameter.SECTOR_KEY, skFacet);

    NameSearchResponse result = svc.search(indexName, nsr, page);

    assertEquals(expected, result.getFacets());

  }
  
  @Test
  public void testDatasetKey() throws IOException {
    
    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFacet(NameSearchParameter.DATASET_KEY);
    Page page = new Page(100);

    Integer key1 = 1000;
    Integer key2 = 2000;

    NameUsageWrapper nuw1 = minimalNameUsage();
    NameUsageWrapper nuw2 = minimalNameUsage();
    NameUsageWrapper nuw3 = minimalNameUsage();
    NameUsageWrapper nuw4 = minimalNameUsage();
    NameUsageWrapper nuw5 = minimalNameUsage();
    NameUsageWrapper nuw6 = minimalNameUsage();
    NameUsageWrapper nuw7 = minimalNameUsage();

    nuw1.getUsage().getName().setDatasetKey(key1);
    nuw2.getUsage().getName().setDatasetKey(key1);
    nuw3.getUsage().getName().setDatasetKey(key1);
    nuw4.getUsage().getName().setDatasetKey(key2);
    nuw5.getUsage().getName().setDatasetKey(key2);
    nuw6.getUsage().getName().setDatasetKey(null);
    nuw7.getUsage().getName().setDatasetKey(null);
 
    NameUsageTransfer transfer = new NameUsageTransfer();

    insert(client, indexName, transfer.toDocument(nuw1));
    insert(client, indexName, transfer.toDocument(nuw2));
    insert(client, indexName, transfer.toDocument(nuw3));
    insert(client, indexName, transfer.toDocument(nuw4));
    insert(client, indexName, transfer.toDocument(nuw5));
    insert(client, indexName, transfer.toDocument(nuw6));
    
    insert(client, indexName, transfer.toDocument(nuw7));
    refreshIndex(client, indexName);

    // Resurrect NameUsageWrapper instances b/c they got pruned upon insert.
    nuw1.getUsage().getName().setDatasetKey(key1);
    nuw2.getUsage().getName().setDatasetKey(key1);
    nuw3.getUsage().getName().setDatasetKey(key1);
    nuw4.getUsage().getName().setDatasetKey(key2);
    nuw5.getUsage().getName().setDatasetKey(key2);
    nuw6.getUsage().getName().setDatasetKey(null);
    nuw7.getUsage().getName().setDatasetKey(null);

    Map<NameSearchParameter, Set<FacetValue<?>>> expectedFacets = new HashMap<>();
    Set<FacetValue<?>> datasetFacet = new TreeSet<>();
    datasetFacet.add(FacetValue.forInteger(key1, 3));
    datasetFacet.add(FacetValue.forInteger(key2, 2));
    expectedFacets.put(NameSearchParameter.DATASET_KEY, datasetFacet);

    NameSearchResponse result = svc.search(indexName, nsr, page);

    assertEquals(expectedFacets, result.getFacets());
    
  }

  private static String getDummyPayload() {
    try {
      NameUsageWrapper dummy = TestEntityGenerator.newNameUsageTaxonWrapper();
      if (NameUsageTransfer.ZIP_PAYLOAD) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
        DeflaterOutputStream dos = new DeflaterOutputStream(baos);
        EsModule.NAME_USAGE_WRITER.writeValue(dos, dummy);
        dos.close();
        return Base64.getEncoder().encodeToString(baos.toByteArray());
      }
      return EsModule.NAME_USAGE_WRITER.writeValueAsString(dummy);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static EsNameUsage newEsNameUsage() {
    EsNameUsage enu = new EsNameUsage();
    enu.setPayload(dummyPayload);
    return enu;
  }
}
