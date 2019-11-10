package org.col.es.name.search;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.zip.InflaterInputStream;

import org.col.api.model.Page;
import org.col.api.search.FacetValue;
import org.col.api.search.NameUsageSearchParameter;
import org.col.api.search.NameUsageSearchResponse;
import org.col.api.search.NameUsageWrapper;
import org.col.es.EsModule;
import org.col.es.model.NameUsageDocument;
import org.col.es.name.NameUsageAggregation;
import org.col.es.name.NameUsageEsResponse;
import org.col.es.name.NameUsageWrapperConverter;
import org.col.es.response.Bucket;
import org.col.es.response.EsFacet;
import org.col.es.response.SearchHit;

import static org.col.api.search.NameUsageSearchParameter.DATASET_KEY;
import static org.col.api.search.NameUsageSearchParameter.DECISION_KEY;
import static org.col.api.search.NameUsageSearchParameter.FIELD;
import static org.col.api.search.NameUsageSearchParameter.ISSUE;
import static org.col.api.search.NameUsageSearchParameter.NAME_ID;
import static org.col.api.search.NameUsageSearchParameter.NAME_INDEX_ID;
import static org.col.api.search.NameUsageSearchParameter.NOM_STATUS;
import static org.col.api.search.NameUsageSearchParameter.PUBLISHED_IN_ID;
import static org.col.api.search.NameUsageSearchParameter.PUBLISHER_KEY;
import static org.col.api.search.NameUsageSearchParameter.RANK;
import static org.col.api.search.NameUsageSearchParameter.SECTOR_KEY;
import static org.col.api.search.NameUsageSearchParameter.STATUS;
import static org.col.api.search.NameUsageSearchParameter.TAXON_ID;
import static org.col.api.search.NameUsageSearchParameter.TYPE;

/**
 * Converts the Elasticsearch response to a NameSearchResponse instance.
 */
class NameSearchResultConverter {

  private final NameUsageEsResponse esResponse;

  NameSearchResultConverter(NameUsageEsResponse esResponse) {
    this.esResponse = esResponse;
  }

  /**
   * Converts the Elasticsearch response object to a NameSearchResponse instance.
   * 
   * @param page
   * @return
   * @throws IOException
   */
  NameUsageSearchResponse transferResponse(Page page) throws IOException {
    int total = esResponse.getHits().getTotalNumHits();
    List<NameUsageWrapper> nameUsages = transferNameUsages();
    Map<NameUsageSearchParameter, Set<FacetValue<?>>> facets = transferFacets();
    return new NameUsageSearchResponse(page, total, nameUsages, facets);
  }

  private List<NameUsageWrapper> transferNameUsages() throws IOException {
    List<SearchHit<NameUsageDocument>> hits = esResponse.getHits().getHits();
    List<NameUsageWrapper> nuws = new ArrayList<>(hits.size());
    for (SearchHit<NameUsageDocument> hit : hits) {
      String payload = hit.getSource().getPayload();
      NameUsageWrapper nuw;
      if (NameUsageWrapperConverter.ZIP_PAYLOAD) {
        nuw = EsModule.readNameUsageWrapper(inflate(payload));
      } else {
        nuw = EsModule.readNameUsageWrapper(payload);
      }
      NameUsageWrapperConverter.enrichPayload(nuw, hit.getSource());
      nuws.add(nuw);
    }
    return nuws;
  }

  private Map<NameUsageSearchParameter, Set<FacetValue<?>>> transferFacets() {
    if (esResponse.getAggregations() == null) {
      return Collections.emptyMap();
    }
    NameUsageAggregation esFacets = esResponse.getAggregations().getContextFilter().getFacetsContainer();
    Map<NameUsageSearchParameter, Set<FacetValue<?>>> facets = new EnumMap<>(NameUsageSearchParameter.class);
    addIfPresent(facets, DATASET_KEY, esFacets.getDatasetKey());
    addIfPresent(facets, DECISION_KEY, esFacets.getDecisionKey());
    addIfPresent(facets, FIELD, esFacets.getField());
    addIfPresent(facets, ISSUE, esFacets.getIssue());
    addIfPresent(facets, NAME_ID, esFacets.getNameId());
    addIfPresent(facets, NAME_INDEX_ID, esFacets.getNameIndexId());
    addIfPresent(facets, NOM_STATUS, esFacets.getNomStatus());
    addIfPresent(facets, PUBLISHED_IN_ID, esFacets.getPublishedInId());
    addIfPresent(facets, PUBLISHER_KEY, esFacets.getPublisherKey());
    addIfPresent(facets, RANK, esFacets.getRank());
    addIfPresent(facets, SECTOR_KEY, esFacets.getSectorKey());
    addIfPresent(facets, STATUS, esFacets.getStatus());
    addIfPresent(facets, TAXON_ID, esFacets.getTaxonId());
    addIfPresent(facets, TYPE, esFacets.getType());
    return facets;
  }

  private static void addIfPresent(Map<NameUsageSearchParameter, Set<FacetValue<?>>> facets, NameUsageSearchParameter param, EsFacet esFacet) {
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

  private static InputStream inflate(String payload) {
    byte[] bytes = Base64.getDecoder().decode(payload.getBytes());
    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
    return new InflaterInputStream(bais);
  }

}
