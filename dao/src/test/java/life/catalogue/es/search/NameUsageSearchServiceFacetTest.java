package life.catalogue.es.search;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.search.*;
import life.catalogue.api.vocab.Issue;
import life.catalogue.es.*;
import life.catalogue.es.NameUsageWrapperConverter;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.util.*;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static life.catalogue.api.search.NameUsageSearchParameter.SECTOR_MODE;
import static org.junit.Assert.assertEquals;

public class NameUsageSearchServiceFacetTest extends EsReadTestBase {

  private static final String dummyPayload = getDummyPayload();

  private static NameUsageSearchServiceEs svc;

  @BeforeClass
  public static void init() {
    svc = new NameUsageSearchServiceEs(esSetupRule.getEsConfig().index.name, esSetupRule.getClient());
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
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFacet(NameUsageSearchParameter.RANK);
    // Don't forget this one; we're going to indexRaw more than 10 docs
    Page page = new Page(100);

    // 4 kingdoms
    EsNameUsage doc = newDocument();
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
    doc.setSectorMode(Sector.Mode.MERGE);
    indexRaw(doc);

    doc = newDocument();
    doc.setRank(Rank.GENUS);
    doc.setSectorMode(Sector.Mode.MERGE);
    indexRaw(doc);

    // 1 species
    doc = newDocument();
    doc.setRank(Rank.SPECIES);
    doc.setSectorMode(Sector.Mode.ATTACH);
    indexRaw(doc);

    NameUsageSearchResponse result = svc.search(indexName(), request, page);

    Map<NameUsageSearchParameter, Set<FacetValue<?>>> expected = new HashMap<>();
    Set<FacetValue<?>> facetValues = new TreeSet<>();
    facetValues.add(FacetValue.forEnum(Rank.class, rankVal(Rank.KINGDOM), 4));
    facetValues.add(FacetValue.forEnum(Rank.class, rankVal(Rank.PHYLUM), 4));
    facetValues.add(FacetValue.forEnum(Rank.class, rankVal(Rank.GENUS), 3));
    facetValues.add(FacetValue.forEnum(Rank.class, rankVal(Rank.CLASS), 2));
    facetValues.add(FacetValue.forEnum(Rank.class, rankVal(Rank.SPECIES), 1));
    expected.put(NameUsageSearchParameter.RANK, facetValues);

    assertEquals(expected, result.getFacets());

    // try new sector mode
    request = new NameUsageSearchRequest();
    request.addFacet(SECTOR_MODE);
    result = svc.search(indexName(), request, page);
    expected = new HashMap<>();
    facetValues = new TreeSet<>();
    facetValues.add(FacetValue.forEnum(Sector.Mode.class, Sector.Mode.MERGE.name(), 2));
    facetValues.add(FacetValue.forEnum(Sector.Mode.class, Sector.Mode.ATTACH.name(), 1));
    expected.put(SECTOR_MODE, facetValues);
    assertEquals(expected, result.getFacets());
  }

  @Test
  public void testTwoFacetsWithoutFilters() throws IOException {

    // ==> Define search
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFacet(NameUsageSearchParameter.RANK);
    request.addFacet(NameUsageSearchParameter.ISSUE);
    Page page = new Page(100);

    EsNameUsage doc = newDocument();
    doc.setRank(Rank.KINGDOM);
    doc.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCEPTED_ID_INVALID));
    System.out.println(EsModule.write(doc));
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

    NameUsageSearchResponse result = svc.search(indexName(), request, page);

    Map<NameUsageSearchParameter, Set<FacetValue<?>>> expected = new HashMap<>();

    Set<FacetValue<?>> rankFacet = new TreeSet<>();
    rankFacet.add(FacetValue.forEnum(Rank.class, rankVal(Rank.KINGDOM), 4));
    rankFacet.add(FacetValue.forEnum(Rank.class, rankVal(Rank.PHYLUM), 4));
    rankFacet.add(FacetValue.forEnum(Rank.class, rankVal(Rank.GENUS), 3));
    rankFacet.add(FacetValue.forEnum(Rank.class, rankVal(Rank.CLASS), 2));
    rankFacet.add(FacetValue.forEnum(Rank.class, rankVal(Rank.SPECIES), 1));
    expected.put(NameUsageSearchParameter.RANK, rankFacet);

    Set<FacetValue<?>> issueFacet = new TreeSet<>();
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.ACCEPTED_NAME_MISSING.name(), 5));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.BASIONYM_ID_INVALID.name(), 3));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.ACCEPTED_ID_INVALID.name(), 2));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.CITATION_UNPARSED.name(), 2));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.CLASSIFICATION_NOT_APPLIED.name(), 1));
    expected.put(NameUsageSearchParameter.ISSUE, issueFacet);

    assertEquals(expected, result.getFacets());

  }

  @Test
  public void testThreeFacetsWithoutFilters() throws IOException {

    // ==> Define search
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFacet(NameUsageSearchParameter.RANK);
    request.addFacet(NameUsageSearchParameter.ISSUE);
    request.addFacet(NameUsageSearchParameter.PUBLISHED_IN_ID);
    Page page = new Page(100);

    String PUB_ID1 = "PUB_0001";
    String PUB_ID2 = "PUB_0002";

    // 4 kingdoms
    EsNameUsage doc = newDocument();
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

    NameUsageSearchResponse result = svc.search(indexName(), request, page);

    Map<NameUsageSearchParameter, Set<FacetValue<?>>> expected = new HashMap<>();

    Set<FacetValue<?>> rankFacet = new TreeSet<>();
    rankFacet.add(FacetValue.forEnum(Rank.class, rankVal(Rank.KINGDOM), 4)); // Descending doc count !!!
    rankFacet.add(FacetValue.forEnum(Rank.class, rankVal(Rank.PHYLUM), 4));
    rankFacet.add(FacetValue.forEnum(Rank.class, rankVal(Rank.GENUS), 3));
    rankFacet.add(FacetValue.forEnum(Rank.class, rankVal(Rank.CLASS), 2));
    rankFacet.add(FacetValue.forEnum(Rank.class, rankVal(Rank.SPECIES), 1));
    expected.put(NameUsageSearchParameter.RANK, rankFacet);

    Set<FacetValue<?>> issueFacet = new TreeSet<>();
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.ACCEPTED_NAME_MISSING.name(), 5));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.BASIONYM_ID_INVALID.name(), 3));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.ACCEPTED_ID_INVALID.name(), 2));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.CITATION_UNPARSED.name(), 2));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.CLASSIFICATION_NOT_APPLIED.name(), 1));
    expected.put(NameUsageSearchParameter.ISSUE, issueFacet);

    Set<FacetValue<?>> pubIdFacet = new TreeSet<>();
    pubIdFacet.add(FacetValue.forString(PUB_ID1, 6));
    pubIdFacet.add(FacetValue.forString(PUB_ID2, 4));
    expected.put(NameUsageSearchParameter.PUBLISHED_IN_ID, pubIdFacet);

    assertEquals(expected, result.getFacets());

  }

  private static String rankVal(Rank rank) {
    return String.valueOf(rank.ordinal());
  }

  @Test
  public void testThreeFacetsWithOneFacetFilter() throws IOException {

    String PUB_ID1 = "PUB_0001";
    String PUB_ID2 = "PUB_0002";

    // Define search
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFacet(NameUsageSearchParameter.RANK);
    request.addFacet(NameUsageSearchParameter.ISSUE);
    request.addFacet(NameUsageSearchParameter.PUBLISHED_IN_ID);
    request.addFilter(NameUsageSearchParameter.PUBLISHED_IN_ID, PUB_ID1);
    Page page = new Page(100);

    EsNameUsage doc = newDocument();
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

    NameUsageSearchResponse result = svc.search(indexName(), request, page);

    Map<NameUsageSearchParameter, Set<FacetValue<?>>> expected = new HashMap<>();

    Set<FacetValue<?>> rankFacet = new TreeSet<>();
    rankFacet.add(FacetValue.forEnum(Rank.class, rankVal(Rank.KINGDOM), 3));
    rankFacet.add(FacetValue.forEnum(Rank.class, rankVal(Rank.PHYLUM), 1));
    rankFacet.add(FacetValue.forEnum(Rank.class, rankVal(Rank.GENUS), 2));
    expected.put(NameUsageSearchParameter.RANK, rankFacet);

    Set<FacetValue<?>> issueFacet = new TreeSet<>();
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.ACCEPTED_NAME_MISSING.name(), 2));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.BASIONYM_ID_INVALID.name(), 3));
    expected.put(NameUsageSearchParameter.ISSUE, issueFacet);

    Set<FacetValue<?>> pubIdFacet = new TreeSet<>();
    pubIdFacet.add(FacetValue.forString(PUB_ID1, 6));
    pubIdFacet.add(FacetValue.forString(PUB_ID2, 4));
    expected.put(NameUsageSearchParameter.PUBLISHED_IN_ID, pubIdFacet);

    // System.out.println("====================================================================");
    // EsModule.writeDebug(System.out,expected);
    // System.out.println("====================================================================");
    System.out.println(EsModule.writeDebug(result.getFacets()));
    // System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");

    assertEquals(expected, result.getFacets());

  }

  @Test
  public void testSectorKey() throws IOException {
    // Define search
    NameUsageSearchRequest nsr = new NameUsageSearchRequest();
    nsr.addFacet(NameUsageSearchParameter.SECTOR_KEY);
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

    Map<NameUsageSearchParameter, Set<FacetValue<?>>> expected = new HashMap<>();
    Set<FacetValue<?>> skFacet = new TreeSet<>();
    skFacet.add(FacetValue.forInteger(key1, 3));
    skFacet.add(FacetValue.forInteger(key2, 2));
    expected.put(NameUsageSearchParameter.SECTOR_KEY, skFacet);

    NameUsageSearchResponse result = svc.search(indexName(), nsr, page);

    assertEquals(expected, result.getFacets());

  }

  @Test
  public void testDatasetKey() throws IOException {

    // Define search
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFacet(NameUsageSearchParameter.DATASET_KEY);
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

    Map<NameUsageSearchParameter, Set<FacetValue<?>>> expectedFacets = new HashMap<>();
    Set<FacetValue<?>> datasetFacet = new TreeSet<>();
    datasetFacet.add(FacetValue.forInteger(key1, 3));
    datasetFacet.add(FacetValue.forInteger(key2, 2));
    expectedFacets.put(NameUsageSearchParameter.DATASET_KEY, datasetFacet);

    NameUsageSearchResponse result = svc.search(indexName(), request, page);

    assertEquals(expectedFacets, result.getFacets());

  }

  private static String getDummyPayload() {
    try {
      NameUsageWrapper dummy = TestEntityGenerator.newNameUsageTaxonWrapper();
      return NameUsageWrapperConverter.encode(dummy);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static EsNameUsage newDocument() {
    EsNameUsage doc = new EsNameUsage();
    doc.setNameStrings(new NameStrings());
    doc.setPayload(dummyPayload);
    return doc;
  }
}
