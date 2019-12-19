package life.catalogue.es.name.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import life.catalogue.api.model.Page;
import life.catalogue.api.search.FacetValue;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.es.EsModule;
import life.catalogue.es.model.NameUsageDocument;
import life.catalogue.es.name.NameUsageAggregation;
import life.catalogue.es.name.NameUsageEsResponse;
import life.catalogue.es.name.NameUsageWrapperConverter;
import life.catalogue.es.response.Bucket;
import life.catalogue.es.response.EsFacet;
import life.catalogue.es.response.SearchHit;

import static life.catalogue.api.search.NameUsageSearchParameter.CATALOGUE_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.DATASET_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.DECISION_MODE;
import static life.catalogue.api.search.NameUsageSearchParameter.FIELD;
import static life.catalogue.api.search.NameUsageSearchParameter.ISSUE;
import static life.catalogue.api.search.NameUsageSearchParameter.NAME_ID;
import static life.catalogue.api.search.NameUsageSearchParameter.NAME_INDEX_ID;
import static life.catalogue.api.search.NameUsageSearchParameter.NOM_STATUS;
import static life.catalogue.api.search.NameUsageSearchParameter.PUBLISHED_IN_ID;
import static life.catalogue.api.search.NameUsageSearchParameter.PUBLISHER_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.RANK;
import static life.catalogue.api.search.NameUsageSearchParameter.SECTOR_DATASET_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.SECTOR_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.STATUS;
import static life.catalogue.api.search.NameUsageSearchParameter.TAXON_ID;
import static life.catalogue.api.search.NameUsageSearchParameter.TYPE;

/**
 * Converts the Elasticsearch response to a NameSearchResponse instance.
 */
class NameUsageSearchResponseFactory {

  private final NameUsageEsResponse esResponse;

  NameUsageSearchResponseFactory(NameUsageEsResponse esResponse) {
    this.esResponse = esResponse;
  }

  /**
   * Converts the Elasticsearch response object to a NameSearchResponse instance.
   * 
   * @param page
   * @return
   * @throws IOException
   */
  NameUsageSearchResponse convertEsResponse(Page page) throws IOException {
    int total = esResponse.getHits().getTotalNumHits();
    List<NameUsageWrapper> nameUsages = convertNameUsageDocuments();
    Map<NameUsageSearchParameter, Set<FacetValue<?>>> facets = generateFacets();
    return new NameUsageSearchResponse(page, total, nameUsages, facets);
  }

  private List<NameUsageWrapper> convertNameUsageDocuments() throws IOException {
    List<SearchHit<NameUsageDocument>> hits = esResponse.getHits().getHits();
    List<NameUsageWrapper> nuws = new ArrayList<>(hits.size());
    for (SearchHit<NameUsageDocument> hit : hits) {
      String payload = hit.getSource().getPayload();
      NameUsageWrapper nuw;
      if (NameUsageWrapperConverter.ZIP_PAYLOAD) {
        nuw = NameUsageWrapperConverter.inflate(payload);
      } else {
        nuw = EsModule.readNameUsageWrapper(payload);
      }
      NameUsageWrapperConverter.enrichPayload(nuw, hit.getSource());
      nuws.add(nuw);
    }
    return nuws;
  }

  private Map<NameUsageSearchParameter, Set<FacetValue<?>>> generateFacets() {
    if (esResponse.getAggregations() == null) {
      return Collections.emptyMap();
    }
    NameUsageAggregation esFacets = esResponse.getAggregations().getContextFilter().getFacetsContainer();
    Map<NameUsageSearchParameter, Set<FacetValue<?>>> facets = new EnumMap<>(NameUsageSearchParameter.class);
    addIfPresent(facets, DATASET_KEY, esFacets.getDatasetKey());
    addIfPresent(facets, CATALOGUE_KEY, esFacets.getCatalogueKey());
    addIfPresent(facets, DECISION_MODE, esFacets.getDecisionMode());
    addIfPresent(facets, FIELD, esFacets.getField());
    addIfPresent(facets, ISSUE, esFacets.getIssue());
    addIfPresent(facets, NAME_ID, esFacets.getNameId());
    addIfPresent(facets, NAME_INDEX_ID, esFacets.getNameIndexId());
    addIfPresent(facets, NOM_STATUS, esFacets.getNomStatus());
    addIfPresent(facets, PUBLISHED_IN_ID, esFacets.getPublishedInId());
    addIfPresent(facets, PUBLISHER_KEY, esFacets.getPublisherKey());
    addIfPresent(facets, RANK, esFacets.getRank());
    addIfPresent(facets, SECTOR_KEY, esFacets.getSectorKey());
    addIfPresent(facets, SECTOR_DATASET_KEY, esFacets.getSectorDatasetKey());
    addIfPresent(facets, STATUS, esFacets.getStatus());
    addIfPresent(facets, TAXON_ID, esFacets.getTaxonId());
    addIfPresent(facets, TYPE, esFacets.getType());
    return facets;
  }

  private static void addIfPresent(Map<NameUsageSearchParameter, Set<FacetValue<?>>> facets, NameUsageSearchParameter param,
      EsFacet esFacet) {
    if (esFacet != null) {
      if (param.type() == String.class) {
        facets.put(param, createStringBuckets(esFacet));
      } else if (param.type() == Integer.class) {
        facets.put(param, createIntBuckets(esFacet));
      } else if (param.type() == UUID.class) {
        facets.put(param, createUuidBuckets(esFacet));
      } else if (param.type().isEnum()) {
        facets.put(param, createEnumBuckets(esFacet, param));
      } else {
        throw new IllegalArgumentException("Unexpected parameter type: " + param.type());
      }
    }
  }

  private static Set<FacetValue<?>> createStringBuckets(EsFacet esFacet) {
    TreeSet<FacetValue<?>> facet = new TreeSet<>();
    for (Bucket b : esFacet.getBucketsContainer().getBuckets()) {
      facet.add(FacetValue.forString(b.getKey(), b.getDocCount()));
    }
    return facet;
  }

  private static Set<FacetValue<?>> createIntBuckets(EsFacet esFacet) {
    TreeSet<FacetValue<?>> facet = new TreeSet<>();
    for (Bucket b : esFacet.getBucketsContainer().getBuckets()) {
      facet.add(FacetValue.forInteger(b.getKey(), b.getDocCount()));
    }
    return facet;
  }

  private static Set<FacetValue<?>> createUuidBuckets(EsFacet esFacet) {
    TreeSet<FacetValue<?>> facet = new TreeSet<>();
    for (Bucket b : esFacet.getBucketsContainer().getBuckets()) {
      facet.add(FacetValue.forUuid(b.getKey(), b.getDocCount()));
    }
    return facet;
  }

  private static <U extends Enum<U>> Set<FacetValue<?>> createEnumBuckets(EsFacet esFacet, NameUsageSearchParameter param) {
    @SuppressWarnings("unchecked")
    Class<U> enumClass = (Class<U>) param.type();
    TreeSet<FacetValue<?>> facet = new TreeSet<>();
    for (Bucket b : esFacet.getBucketsContainer().getBuckets()) {
      facet.add(FacetValue.forEnum(enumClass, b.getKey(), b.getDocCount()));
    }
    return facet;
  }

}
