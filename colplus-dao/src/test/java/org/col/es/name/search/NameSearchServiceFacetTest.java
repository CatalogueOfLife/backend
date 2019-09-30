package org.col.es.name.search;

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
import org.col.es.EsModule;
import org.col.es.EsReadTestBase;
import org.col.es.model.NameUsageDocument;
import org.col.es.name.NameUsageWrapperConverter;
import org.col.es.name.search.NameUsageSearchService;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NameSearchServiceFacetTest extends EsReadTestBase {

  private static final String dummyPayload = getDummyPayload();

  private static NameUsageSearchService svc;

  @BeforeClass
  public static void init() {
    svc = new NameUsageSearchService(indexName, esSetupRule.getEsClient());
  }

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
  public void testSingleFacetWithoutFilters() throws IOException {

    // ==> Define search
    NameSearchRequest request = new NameSearchRequest();
    request.addFacet(NameSearchParameter.RANK);
    // Don't forget this one; we're going to indexRaw more than 10 docs
    Page page = new Page(100);

    // 4 kingdoms
    NameUsageDocument doc = newDocument();
    doc.setRank(Rank.KINGDOM);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.KINGDOM);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.KINGDOM);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.KINGDOM);
    indexRaw(doc);

    // 4 Phylae
    doc = newDocument();
    doc.setRank(Rank.PHYLUM);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.PHYLUM);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.PHYLUM);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.PHYLUM);
    indexRaw(doc);

    // 2 classes
    doc = newDocument();
    doc.setRank(Rank.CLASS);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.CLASS);
    indexRaw(doc);

    // Zero orders & families

    // 3 genera
    doc = newDocument();
    doc.setRank(Rank.GENUS);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.GENUS);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.GENUS);
    indexRaw(doc);

    // 1 species
    doc = newDocument();
    doc.setRank(Rank.SPECIES);
    indexRaw(doc);

    NameSearchResponse result = svc.search(indexName, request, page);

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

    // ==> Define search
    NameSearchRequest request = new NameSearchRequest();
    request.addFacet(NameSearchParameter.RANK);
    request.addFacet(NameSearchParameter.ISSUE);
    Page page = new Page(100);

    NameUsageDocument doc = newDocument();
    doc.setRank(Rank.KINGDOM);
    doc.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCEPTED_ID_INVALID));
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.KINGDOM);
    doc.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.BASIONYM_ID_INVALID));
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.KINGDOM);
    doc.setIssues(EnumSet.of(Issue.BASIONYM_ID_INVALID));
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.KINGDOM);
    doc.setIssues(EnumSet.of(Issue.BASIONYM_ID_INVALID));
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.PHYLUM);
    doc.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING));
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.PHYLUM);
    doc.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.CITATION_UNPARSED));
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.PHYLUM);
    doc.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCEPTED_ID_INVALID, Issue.CLASSIFICATION_NOT_APPLIED));
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.PHYLUM);
    indexRaw(doc);

    // 2 classes
    doc = newDocument();
    doc.setRank(Rank.CLASS);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.CLASS);
    doc.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.GENUS);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.GENUS);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.GENUS);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.SPECIES);
    indexRaw(doc);

    // ACCEPTED_NAME_MISSING: 5 docs
    // BASIONYM_ID_INVALID: 3 docs
    // ACCEPTED_ID_INVALID: 2 docs
    // CITATION_UNPARSED: 2 docs
    // CLASSIFICATION_NOT_APPLIED: 1 doc

    NameSearchResponse result = svc.search(indexName, request, page);

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

    // ==> Define search
    NameSearchRequest request = new NameSearchRequest();
    request.addFacet(NameSearchParameter.RANK);
    request.addFacet(NameSearchParameter.ISSUE);
    request.addFacet(NameSearchParameter.PUBLISHED_IN_ID);
    Page page = new Page(100);

    String PUB_ID1 = "PUB_0001";
    String PUB_ID2 = "PUB_0002";

    // 4 kingdoms
    NameUsageDocument doc = newDocument();
    doc.setRank(Rank.KINGDOM);
    doc.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCEPTED_ID_INVALID));
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.KINGDOM);
    doc.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.BASIONYM_ID_INVALID));
    doc.setPublishedInId(PUB_ID1);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.KINGDOM);
    doc.setIssues(EnumSet.of(Issue.BASIONYM_ID_INVALID));
    doc.setPublishedInId(PUB_ID1);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.KINGDOM);
    doc.setIssues(EnumSet.of(Issue.BASIONYM_ID_INVALID));
    doc.setPublishedInId(PUB_ID1);
    indexRaw(doc);

    // 4 Phylae
    doc = newDocument();
    doc.setPublishedInId(PUB_ID1);
    doc.setRank(Rank.PHYLUM);
    doc.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING));
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.PHYLUM);
    doc.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.CITATION_UNPARSED));
    doc.setPublishedInId(PUB_ID2);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.PHYLUM);
    doc.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCEPTED_ID_INVALID, Issue.CLASSIFICATION_NOT_APPLIED));
    doc.setPublishedInId(PUB_ID2);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.PHYLUM);
    indexRaw(doc);

    // 2 classes
    doc = newDocument();
    doc.setRank(Rank.CLASS);
    doc.setPublishedInId(PUB_ID2);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.CLASS);
    doc.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    indexRaw(doc);

    // Zero orders & families

    // 3 genera
    doc = newDocument();
    doc.setRank(Rank.GENUS);
    doc.setPublishedInId(PUB_ID1);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.GENUS);
    doc.setPublishedInId(PUB_ID1);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.GENUS);
    indexRaw(doc);

    // 1 species
    doc = newDocument();
    doc.setRank(Rank.SPECIES);
    doc.setPublishedInId(PUB_ID2);
    indexRaw(doc);

    // ACCEPTED_NAME_MISSING: 5 docs
    // BASIONYM_ID_INVALID: 3 docs
    // ACCEPTED_ID_INVALID: 2 docs
    // CITATION_UNPARSED: 2 docs
    // CLASSIFICATION_NOT_APPLIED: 1 doc

    // PUB_ID1: 6 docs
    // PUB_ID2: 4 docs

    NameSearchResponse result = svc.search(indexName, request, page);

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
  public void testThreeFacetsWithOneFacetFilter() throws IOException {

    String PUB_ID1 = "PUB_0001";
    String PUB_ID2 = "PUB_0002";

    // Define search
    NameSearchRequest request = new NameSearchRequest();
    request.addFacet(NameSearchParameter.RANK);
    request.addFacet(NameSearchParameter.ISSUE);
    request.addFacet(NameSearchParameter.PUBLISHED_IN_ID);
    request.addFilter(NameSearchParameter.PUBLISHED_IN_ID, PUB_ID1);
    Page page = new Page(100);

    NameUsageDocument doc = newDocument();
    doc.setRank(Rank.KINGDOM);
    doc.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCEPTED_ID_INVALID));
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.KINGDOM);
    doc.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.BASIONYM_ID_INVALID));
    doc.setPublishedInId(PUB_ID1);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.KINGDOM);
    doc.setIssues(EnumSet.of(Issue.BASIONYM_ID_INVALID));
    doc.setPublishedInId(PUB_ID1);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.KINGDOM);
    doc.setIssues(EnumSet.of(Issue.BASIONYM_ID_INVALID));
    doc.setPublishedInId(PUB_ID1);
    indexRaw(doc);

    doc = newDocument();
    doc.setPublishedInId(PUB_ID1);
    doc.setRank(Rank.PHYLUM);
    doc.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING));
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.PHYLUM);
    doc.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.CITATION_UNPARSED));
    doc.setPublishedInId(PUB_ID2);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.PHYLUM);
    doc.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCEPTED_ID_INVALID, Issue.CLASSIFICATION_NOT_APPLIED));
    doc.setPublishedInId(PUB_ID2);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.PHYLUM);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.CLASS);
    doc.setPublishedInId(PUB_ID2);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.CLASS);
    doc.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.GENUS);
    doc.setPublishedInId(PUB_ID1);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.GENUS);
    doc.setPublishedInId(PUB_ID1);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.GENUS);
    indexRaw(doc);

    // 1 species
    doc = newDocument();
    doc.setRank(Rank.SPECIES);
    doc.setPublishedInId(PUB_ID2);
    indexRaw(doc);

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

    NameSearchResponse result = svc.search(indexName, request, page);

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

    index(nuw1, nuw2, nuw3, nuw4, nuw5, nuw6, nuw7);

    // Resurrect NameUsageWrapper instances b/c they got pruned upon indexRaw.
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

    index(nuw1, nuw2, nuw3, nuw4, nuw5, nuw6, nuw7);

    // Resurrect NameUsageWrapper instances b/c they got pruned upon indexRaw.
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
    NameSearchRequest request = new NameSearchRequest();
    request.addFacet(NameSearchParameter.DATASET_KEY);
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

    index(nuw1, nuw2, nuw3, nuw4, nuw5, nuw6, nuw7);

    // Resurrect NameUsageWrapper instances b/c they got pruned upon indexRaw.
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

    NameSearchResponse result = svc.search(indexName, request, page);

    assertEquals(expectedFacets, result.getFacets());

  }

  private static String getDummyPayload() {
    try {
      NameUsageWrapper dummy = TestEntityGenerator.newNameUsageTaxonWrapper();
      if (NameUsageWrapperConverter.ZIP_PAYLOAD) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
        try (DeflaterOutputStream dos = new DeflaterOutputStream(baos)) {
          EsModule.NAME_USAGE_WRITER.writeValue(dos, dummy);
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray());
      }
      return EsModule.NAME_USAGE_WRITER.writeValueAsString(dummy);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static NameUsageDocument newDocument() {
    NameUsageDocument doc = new NameUsageDocument();
    doc.setPayload(dummyPayload);
    return doc;
  }
}
