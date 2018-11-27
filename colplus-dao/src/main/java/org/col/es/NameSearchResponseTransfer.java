package org.col.es;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.col.api.model.NameUsage;
import org.col.api.model.Page;
import org.col.api.search.FacetValue;
import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchResponse;
import org.col.api.search.NameUsageWrapper;
import org.col.es.model.EsNameUsage;
import org.col.es.response.EsFacet;
import org.col.es.response.EsFacets;
import org.col.es.response.EsNameSearchResponse;
import org.col.es.response.SearchHit;

import static org.col.api.search.NameSearchParameter.DATASET_KEY;
import static org.col.api.search.NameSearchParameter.FIELD;
import static org.col.api.search.NameSearchParameter.ISSUE;
import static org.col.api.search.NameSearchParameter.NAME_ID;
import static org.col.api.search.NameSearchParameter.NAME_INDEX_ID;
import static org.col.api.search.NameSearchParameter.NOM_STATUS;
import static org.col.api.search.NameSearchParameter.PUBLISHED_IN_ID;
import static org.col.api.search.NameSearchParameter.RANK;
import static org.col.api.search.NameSearchParameter.STATUS;
import static org.col.api.search.NameSearchParameter.TYPE;

class NameSearchResponseTransfer {

  private final EsNameSearchResponse esRresponse;
  private final Page page;

  NameSearchResponseTransfer(EsNameSearchResponse response, Page page) {
    this.esRresponse = response;
    this.page = page;
  }

  public NameSearchResponse transferResponse() {
    int total = esRresponse.getHits().getTotal();
    List<NameUsageWrapper<NameUsage>> nameUsages = transferNameUsages();
    if (esRresponse.getAggregations() == null) {
      return new NameSearchResponse(page, total, nameUsages);
    }
    Map<NameSearchParameter, List<FacetValue<?>>> facets = transferFacets();
    return new NameSearchResponse(page, total, nameUsages, facets);
  }

  private List<NameUsageWrapper<NameUsage>> transferNameUsages() {
    return esRresponse.getHits().getHits().stream().map(this::convert).collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  private NameUsageWrapper<NameUsage> convert(SearchHit<EsNameUsage> hit) {
    try {
      return (NameUsageWrapper<NameUsage>) EsModule.NAME_USAGE_READER.readValue(hit.getSource().getPayload());
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  private Map<NameSearchParameter, List<FacetValue<?>>> transferFacets() {
    EsFacets esFacets = esRresponse.getAggregations().getContextFilter().getFacetsContainer();
    Map<NameSearchParameter, List<FacetValue<?>>> facets = new LinkedHashMap<>();
    addIfPresent(facets, DATASET_KEY, esFacets.getDatasetKey());
    addIfPresent(facets, FIELD, esFacets.getField());
    addIfPresent(facets, ISSUE, esFacets.getIssue());
    addIfPresent(facets, NAME_ID, esFacets.getNameId());
    addIfPresent(facets, NAME_INDEX_ID, esFacets.getNameIndexId());
    addIfPresent(facets, NOM_STATUS, esFacets.getNomStatus());
    addIfPresent(facets, PUBLISHED_IN_ID, esFacets.getPublishedInId());
    addIfPresent(facets, RANK, esFacets.getRank());
    addIfPresent(facets, STATUS, esFacets.getStatus());
    addIfPresent(facets, TYPE, esFacets.getType());
    return facets;
  }

  private static void addIfPresent(Map<NameSearchParameter, List<FacetValue<?>>> facets, NameSearchParameter param, EsFacet esFacet) {
    if (esFacet != null) {
      if (param.type() == String.class) {
        facets.put(param, createStringBuckets(esFacet));
      } else if (param.type() == Integer.class) {
        facets.put(param, createIntBuckets(esFacet));
      } else if (param.type().isEnum()) {
        facets.put(param, createEnumBuckets(esFacet, param));
      } else {
        throw new AssertionError("Unexpected type of NameSearchParameter");
      }
    }
  }

  private static List<FacetValue<?>> createStringBuckets(EsFacet esFacet) {
    return esFacet.getBucketsContainer()
        .getBuckets()
        .stream()
        .map(b -> new FacetValue<String>(b.getKey().toString(), b.getDocCount()))
        .collect(Collectors.toList());
  }

  private static List<FacetValue<?>> createIntBuckets(EsFacet esFacet) {
    return esFacet.getBucketsContainer()
        .getBuckets()
        .stream()
        .map(b -> new FacetValue<Integer>((Integer) b.getKey(), b.getDocCount()))
        .collect(Collectors.toList());
  }

  private static List<FacetValue<?>> createEnumBuckets(EsFacet esFacet, NameSearchParameter param) {
    return esFacet.getBucketsContainer()
        .getBuckets()
        .stream()
        .map(b -> {
          Enum<?> e = (Enum<?>) param.type().getEnumConstants()[(Integer) b.getKey()];
          return new FacetValue<Enum<?>>(e, b.getDocCount());
        })
        .collect(Collectors.toList());
  }

}
