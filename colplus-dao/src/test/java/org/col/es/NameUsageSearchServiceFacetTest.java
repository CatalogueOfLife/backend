package org.col.es;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.col.api.TestEntityGenerator;
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

  private static final String indexName = "name_usage_test";
  private static final String dummyPayload = getDummyPayload();

  private static RestClient client;
  private static NameUsageSearchService svc;

  @BeforeClass
  public static void init() {
    client = esSetupRule.getEsClient();
    svc = new NameUsageSearchService(esSetupRule.getEsClient());
  }

  @AfterClass
  public static void shutdown() throws IOException {
    client.close();
  }

  @Before
  public void before() {
    EsUtil.deleteIndex(client, indexName);
    EsUtil.createIndex(client, indexName, getEsConfig().nameUsage);
  }

  @Test
  public void testSingleFacetWithoutFilters() throws InvalidQueryException, JsonProcessingException {
    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFacet(NameSearchParameter.RANK);
    // Don't forget this one; we're going to insert more than 10 docs
    Page page = new Page(100);

    // 4 kingdoms
    EsNameUsage enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setScientificName("Animalia");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setScientificName("Plantae");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setScientificName("Fungi");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setScientificName("Bacteria");
    insert(client, indexName, enu);

    // 4 Phylae
    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setScientificName("Arthropoda");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setScientificName("Brachiopoda");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setScientificName("Chordata");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setScientificName("Mollusca");
    insert(client, indexName, enu);

    // 2 classes
    enu = newEsNameUsage();
    enu.setRank(Rank.CLASS);
    enu.setScientificName("Mammalia");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.CLASS);
    enu.setScientificName("Aves");
    insert(client, indexName, enu);

    // Zero orders & families

    // 3 genera
    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setScientificName("Homo");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setScientificName("Larus");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setScientificName("Abies");
    insert(client, indexName, enu);

    // 1 species
    enu = newEsNameUsage();
    enu.setRank(Rank.SPECIES);
    enu.setScientificName("Larus fuscus");
    insert(client, indexName, enu);

    refreshIndex(client, indexName);

    NameSearchResponse result = svc.search(indexName, nsr, page);

    Map<NameSearchParameter, List<FacetValue<?>>> expected = new HashMap<>();
    List<FacetValue<?>> rankFacet = new ArrayList<>();
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.KINGDOM.ordinal(), 4)); // Descending doc count !!!
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.PHYLUM.ordinal(), 4));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.GENUS.ordinal(), 3));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.CLASS.ordinal(), 2));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.SPECIES.ordinal(), 1));
    expected.put(NameSearchParameter.RANK, rankFacet);
    assertEquals(expected, result.getFacets());

  }

  @Test
  public void testTwoFacetsWithoutFilters() throws InvalidQueryException, JsonProcessingException {
    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFacet(NameSearchParameter.RANK);
    // Don't forget this one; we're going to insert more than 10 docs
    Page page = new Page(100);

    // 4 kingdoms
    EsNameUsage enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCEPTED_ID_INVALID));
    enu.setScientificName("Animalia");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.BASIONYM_ID_INVALID));
    enu.setScientificName("Plantae");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.BASIONYM_ID_INVALID));
    enu.setScientificName("Fungi");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.BASIONYM_ID_INVALID));
    enu.setScientificName("Bacteria");
    insert(client, indexName, enu);

    // 4 Phylae
    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING));
    enu.setScientificName("Arthropoda");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.CITATION_UNPARSED));
    enu.setScientificName("Brachiopoda");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCEPTED_ID_INVALID, Issue.CLASSIFICATION_NOT_APPLIED));
    enu.setScientificName("Chordata");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setScientificName("Mollusca");
    insert(client, indexName, enu);

    // 2 classes
    enu = newEsNameUsage();
    enu.setRank(Rank.CLASS);
    enu.setScientificName("Mammalia");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.CLASS);
    enu.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    enu.setScientificName("Aves");
    insert(client, indexName, enu);

    // Zero orders & families

    // 3 genera
    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setScientificName("Homo");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setScientificName("Larus");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setScientificName("Abies");
    insert(client, indexName, enu);

    // 1 species
    enu = newEsNameUsage();
    enu.setRank(Rank.SPECIES);
    enu.setScientificName("Larus fuscus");
    insert(client, indexName, enu);

    // ACCEPTED_NAME_MISSING: 5 docs
    // BASIONYM_ID_INVALID: 3 docs
    // ACCEPTED_ID_INVALID: 2 docs
    // CITATION_UNPARSED: 2 docs
    // CLASSIFICATION_NOT_APPLIED: 1 doc

    refreshIndex(client, indexName);

    NameSearchResponse result = svc.search(indexName, nsr, page);

    Map<NameSearchParameter, List<FacetValue<?>>> expected = new HashMap<>();

    List<FacetValue<?>> rankFacet = new ArrayList<>();
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.KINGDOM.ordinal(), 4)); // Descending doc count !!!
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.PHYLUM.ordinal(), 4));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.GENUS.ordinal(), 3));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.CLASS.ordinal(), 2));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.SPECIES.ordinal(), 1));
    expected.put(NameSearchParameter.RANK, rankFacet);

    List<FacetValue<?>> issueFacet = new ArrayList<>();
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.ACCEPTED_NAME_MISSING.ordinal(), 5));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.BASIONYM_ID_INVALID.ordinal(), 3));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.ACCEPTED_ID_INVALID.ordinal(), 2));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.CITATION_UNPARSED.ordinal(), 2));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.CLASSIFICATION_NOT_APPLIED.ordinal(), 1));
    assertEquals(expected, result.getFacets());

  }

  @Test
  public void testThreeFacetsWithoutFilters() throws InvalidQueryException, JsonProcessingException {
    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFacet(NameSearchParameter.RANK);
    // Don't forget this one; we're going to insert more than 10 docs
    Page page = new Page(100);

    String PUB_ID1 = "PUB_0001";
    String PUB_ID2 = "PUB_0002";

    // 4 kingdoms
    EsNameUsage enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCEPTED_ID_INVALID));
    enu.setScientificName("Animalia");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.BASIONYM_ID_INVALID));
    enu.setPublishedInId(PUB_ID1);
    enu.setScientificName("Plantae");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.BASIONYM_ID_INVALID));
    enu.setPublishedInId(PUB_ID1);
    enu.setScientificName("Fungi");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.KINGDOM);
    enu.setIssues(EnumSet.of(Issue.BASIONYM_ID_INVALID));
    enu.setPublishedInId(PUB_ID1);
    enu.setScientificName("Bacteria");
    insert(client, indexName, enu);

    // 4 Phylae
    enu = newEsNameUsage();
    enu.setPublishedInId(PUB_ID1);
    enu.setRank(Rank.PHYLUM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING));
    enu.setScientificName("Arthropoda");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.CITATION_UNPARSED));
    enu.setPublishedInId(PUB_ID2);
    enu.setScientificName("Brachiopoda");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCEPTED_ID_INVALID, Issue.CLASSIFICATION_NOT_APPLIED));
    enu.setPublishedInId(PUB_ID2);
    enu.setScientificName("Chordata");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.PHYLUM);
    enu.setScientificName("Mollusca");
    insert(client, indexName, enu);

    // 2 classes
    enu = newEsNameUsage();
    enu.setRank(Rank.CLASS);
    enu.setPublishedInId(PUB_ID2);
    enu.setScientificName("Mammalia");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.CLASS);
    enu.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    enu.setScientificName("Aves");
    insert(client, indexName, enu);

    // Zero orders & families

    // 3 genera
    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setPublishedInId(PUB_ID1);
    enu.setScientificName("Homo");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setPublishedInId(PUB_ID1);
    enu.setScientificName("Larus");
    insert(client, indexName, enu);

    enu = newEsNameUsage();
    enu.setRank(Rank.GENUS);
    enu.setScientificName("Abies");
    insert(client, indexName, enu);

    // 1 species
    enu = newEsNameUsage();
    enu.setRank(Rank.SPECIES);
    enu.setPublishedInId(PUB_ID2);
    enu.setScientificName("Larus fuscus");
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

    Map<NameSearchParameter, List<FacetValue<?>>> expected = new HashMap<>();

    List<FacetValue<?>> rankFacet = new ArrayList<>();
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.KINGDOM.ordinal(), 4)); // Descending doc count !!!
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.PHYLUM.ordinal(), 4));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.GENUS.ordinal(), 3));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.CLASS.ordinal(), 2));
    rankFacet.add(FacetValue.forEnum(Rank.class, Rank.SPECIES.ordinal(), 1));
    expected.put(NameSearchParameter.RANK, rankFacet);

    List<FacetValue<?>> issueFacet = new ArrayList<>();
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.ACCEPTED_NAME_MISSING.ordinal(), 5));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.BASIONYM_ID_INVALID.ordinal(), 3));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.ACCEPTED_ID_INVALID.ordinal(), 2));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.CITATION_UNPARSED.ordinal(), 2));
    issueFacet.add(FacetValue.forEnum(Issue.class, Issue.CLASSIFICATION_NOT_APPLIED.ordinal(), 1));
    
    List<FacetValue<?>> pubIdFacet = new ArrayList<>();
    pubIdFacet.add(FacetValue.forString(PUB_ID1, 6));
    pubIdFacet.add(FacetValue.forString(PUB_ID2, 4));
    
    assertEquals(expected, result.getFacets());

  }

  private static String getDummyPayload() {
    NameUsageWrapper<Taxon> dummy = TestEntityGenerator.newNameUsageTaxonWrapper();
    try {
      return EsModule.NAME_USAGE_WRITER.writeValueAsString(dummy);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static EsNameUsage newEsNameUsage() {
    EsNameUsage enu = new EsNameUsage();
    enu.setPayload(dummyPayload);
    return enu;
  }
}
