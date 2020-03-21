package life.catalogue.es.nu.search;

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
import life.catalogue.es.EsNameUsage;
import life.catalogue.es.UpwardConverter;
import life.catalogue.es.nu.NameUsageWrapperConverter;
import life.catalogue.es.response.Bucket;
import life.catalogue.es.response.EsFacet;
import life.catalogue.es.response.EsResponse;
import life.catalogue.es.response.FacetsContainer;
import life.catalogue.es.response.SearchHit;

/**
 * Converts the Elasticsearch response to a NameSearchResponse instance.
 */
class ResponseConverter implements UpwardConverter<EsResponse<EsNameUsage>, NameUsageSearchResponse> {

  private final EsResponse<EsNameUsage> esResponse;

  ResponseConverter(EsResponse<EsNameUsage> esResponse) {
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
    List<SearchHit<EsNameUsage>> hits = esResponse.getHits().getHits();
    List<NameUsageWrapper> nuws = new ArrayList<>(hits.size());
    for (SearchHit<EsNameUsage> hit : hits) {
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
    FacetsContainer facets = extractFacetsFromResponse();
    if (facets == null) {
      return Collections.emptyMap();
    }
    Map<NameUsageSearchParameter, Set<FacetValue<?>>> result = new EnumMap<>(NameUsageSearchParameter.class);
    for (String paramName : facets.keySet()) {
      if (Character.isLowerCase(paramName.codePointAt(0))) { // some native ES property in the response
        continue;
      }
      NameUsageSearchParameter param;
      try {
        param = NameUsageSearchParameter.valueOf(paramName);
      } catch (IllegalArgumentException e) {
        continue;
      }
      EsFacet esFacet = facets.getFacet(param);
      result.put(param, convert(param, esFacet));
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private FacetsContainer extractFacetsFromResponse() {
    Map<String, Object> aggs = esResponse.getAggregations();
    if (aggs != null) {
      Map<String, Object> globalAgg = (Map<String, Object>) aggs.get(FacetsTranslator.GLOBAL_AGG_LABEL);
      if (globalAgg != null) {
        // should always be the case as soon as we have aggregations in the first place, but let's check anyhow
        Map<String, Object> filterAgg = (Map<String, Object>) globalAgg.get(FacetsTranslator.FILTER_AGG_LABEL);
        if (filterAgg != null) { // idem
          return new FacetsContainer(filterAgg);
        }
      }
    }
    return null;
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
    if (esFacet.getFacetValues() != null && esFacet.getFacetValues().getBuckets() != null) {
      for (Bucket b : esFacet.getFacetValues().getBuckets()) {
        facet.add(FacetValue.forString(b.getKey(), b.getDocCount()));
      }
    }
    return facet;
  }

  private static Set<FacetValue<?>> createIntBuckets(EsFacet esFacet) {
    TreeSet<FacetValue<?>> facet = new TreeSet<>();
    if (esFacet.getFacetValues() != null && esFacet.getFacetValues().getBuckets() != null) {
      for (Bucket b : esFacet.getFacetValues().getBuckets()) {
        facet.add(FacetValue.forInteger(b.getKey(), b.getDocCount()));
      }
    }
    return facet;
  }

  private static Set<FacetValue<?>> createUuidBuckets(EsFacet esFacet) {
    TreeSet<FacetValue<?>> facet = new TreeSet<>();
    if (esFacet.getFacetValues() != null && esFacet.getFacetValues().getBuckets() != null) {
      for (Bucket b : esFacet.getFacetValues().getBuckets()) {
        facet.add(FacetValue.forUuid(b.getKey(), b.getDocCount()));
      }
    }
    return facet;
  }

  private static <U extends Enum<U>> Set<FacetValue<?>> createEnumBuckets(EsFacet esFacet, NameUsageSearchParameter param) {
    @SuppressWarnings("unchecked")
    Class<U> enumClass = (Class<U>) param.type();
    TreeSet<FacetValue<?>> facet = new TreeSet<>();
    if (esFacet.getFacetValues() != null && esFacet.getFacetValues().getBuckets() != null) {
      for (Bucket b : esFacet.getFacetValues().getBuckets()) {
        facet.add(FacetValue.forEnum(enumClass, b.getKey(), b.getDocCount()));
      }
    }
    return facet;
  }

}
