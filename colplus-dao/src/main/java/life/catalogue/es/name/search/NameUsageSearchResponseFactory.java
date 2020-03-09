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
import java.util.stream.Collectors;
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
    NameUsageAggregation agg = esResponse.getAggregations().getContextFilter().getFacetsContainer();
    Map<NameUsageSearchParameter, Set<FacetValue<?>>> result = new EnumMap<>(NameUsageSearchParameter.class);
    for (String key : agg.keySet()) {
      NameUsageSearchParameter param;
      try {
        param = NameUsageSearchParameter.fromFacet(key);
      } catch (IllegalArgumentException e) { // stuff like "doc_count"
        continue;
      }
      EsFacet esFacet = agg.getFacet(param);
      result.put(param, convert(param, esFacet));
    }
    return result;
  }

  private static Set<FacetValue<?>> convert(NameUsageSearchParameter param, EsFacet esFacet) {
    if (param.type() == Integer.class) {
      return createIntBuckets(esFacet);
    } else if (param.type() == UUID.class) {
      return createUuidBuckets(esFacet);
    } else if (param.type().isEnum()) {
      return createEnumBuckets(esFacet, param);
    } else {
      return createStringBuckets(esFacet);
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
