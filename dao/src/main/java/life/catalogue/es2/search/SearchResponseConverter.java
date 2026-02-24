package life.catalogue.es2.search;

import life.catalogue.api.model.Page;
import life.catalogue.api.search.FacetValue;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.dao.DatasetInfoCache;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import javax.annotation.Nullable;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;

/**
 * Converts the Elasticsearch response to a NameSearchResponse instance.
 */
class SearchResponseConverter {

  private final SearchResponse<NameUsageWrapper> esResponse;

  SearchResponseConverter(SearchResponse<NameUsageWrapper> esResponse) {
    this.esResponse = esResponse;
  }

  NameUsageSearchResponse convertEsResponse(Page page) throws IOException {
    int total = (int) esResponse.hits().total().value();
    List<NameUsageWrapper> nameUsages = convertNameUsageDocuments();
    Map<NameUsageSearchParameter, Set<FacetValue<?>>> facets = generateFacets();
    return new NameUsageSearchResponse(page, total, nameUsages, facets);
  }

  private List<NameUsageWrapper> convertNameUsageDocuments() throws IOException {
    return esResponse.hits().hits().stream()
      .map(Hit::source)
      .toList();
  }

  private Map<NameUsageSearchParameter, Set<FacetValue<?>>> generateFacets() {
    if (esResponse.aggregations() == null || esResponse.aggregations().isEmpty()) {
      return Collections.emptyMap();
    }

    Aggregate globalAgg = esResponse.aggregations().get(FacetsTranslator.GLOBAL_AGG_LABEL);
    if (globalAgg == null || !globalAgg.isGlobal()) {
      return Collections.emptyMap();
    }

    Map<String, Aggregate> globalSubs = globalAgg.global().aggregations();
    Aggregate filterAgg = globalSubs.get(FacetsTranslator.FILTER_AGG_LABEL);
    if (filterAgg == null || !filterAgg.isFilter()) {
      return Collections.emptyMap();
    }

    Map<String, Aggregate> facetAggs = filterAgg.filter().aggregations();
    Map<NameUsageSearchParameter, Set<FacetValue<?>>> result = new EnumMap<>(NameUsageSearchParameter.class);

    for (Map.Entry<String, Aggregate> entry : facetAggs.entrySet()) {
      String paramName = entry.getKey();
      if (Character.isLowerCase(paramName.codePointAt(0))) {
        continue; // some native ES property
      }
      NameUsageSearchParameter param;
      try {
        param = NameUsageSearchParameter.valueOf(paramName);
      } catch (IllegalArgumentException e) {
        continue;
      }

      Aggregate facetFilterAgg = entry.getValue();
      if (!facetFilterAgg.isFilter()) continue;

      Aggregate termsAgg = facetFilterAgg.filter().aggregations().get(FacetsTranslator.FACET_AGG_LABEL);
      if (termsAgg == null) continue;

      result.put(param, extractBuckets(param, termsAgg));
    }
    return result;
  }

  private static Set<FacetValue<?>> extractBuckets(NameUsageSearchParameter param, Aggregate termsAgg) {
    List<BucketEntry> entries = new ArrayList<>();
    if (termsAgg.isSterms()) {
      for (var b : termsAgg.sterms().buckets().array()) {
        entries.add(new BucketEntry(b.key().stringValue(), b.docCount()));
      }
    } else if (termsAgg.isLterms()) {
      for (var b : termsAgg.lterms().buckets().array()) {
        entries.add(new BucketEntry(String.valueOf(b.key()), b.docCount()));
      }
    } else if (termsAgg.isDterms()) {
      for (var b : termsAgg.dterms().buckets().array()) {
        entries.add(new BucketEntry(String.valueOf(b.key()), b.docCount()));
      }
    }
    return convert(param, entries);
  }

  private record BucketEntry(String key, long docCount) {}

  private static Set<FacetValue<?>> convert(NameUsageSearchParameter param, List<BucketEntry> entries) {
    if (param == NameUsageSearchParameter.DATASET_KEY) {
      return createIntBuckets(entries, DatasetInfoCache.CACHE.labels::get);
    } else if (param.type() == Integer.class) {
      return createIntBuckets(entries);
    } else if (param.type() == UUID.class) {
      return createUuidBuckets(entries);
    } else if (param.type().isEnum()) {
      return createEnumBuckets(entries, param);
    } else {
      return createStringBuckets(entries);
    }
  }

  private static Set<FacetValue<?>> createStringBuckets(List<BucketEntry> entries) {
    TreeSet<FacetValue<?>> facet = new TreeSet<>();
    for (BucketEntry b : entries) {
      facet.add(FacetValue.forString(b.key(), (int) b.docCount()));
    }
    return facet;
  }

  private static Set<FacetValue<?>> createIntBuckets(List<BucketEntry> entries) {
    return createIntBuckets(entries, null);
  }

  private static Set<FacetValue<?>> createIntBuckets(List<BucketEntry> entries, @Nullable Function<Integer, String> labelFunc) {
    TreeSet<FacetValue<?>> facet = new TreeSet<>();
    for (BucketEntry b : entries) {
      facet.add(FacetValue.forInteger(b.key(), (int) b.docCount(), labelFunc));
    }
    return facet;
  }

  private static Set<FacetValue<?>> createUuidBuckets(List<BucketEntry> entries) {
    TreeSet<FacetValue<?>> facet = new TreeSet<>();
    for (BucketEntry b : entries) {
      facet.add(FacetValue.forUuid(b.key(), (int) b.docCount()));
    }
    return facet;
  }

  private static <U extends Enum<U>> Set<FacetValue<?>> createEnumBuckets(List<BucketEntry> entries, NameUsageSearchParameter param) {
    @SuppressWarnings("unchecked")
    Class<U> enumClass = (Class<U>) param.type();
    TreeSet<FacetValue<?>> facet = new TreeSet<>();
    for (BucketEntry b : entries) {
      facet.add(FacetValue.forEnum(enumClass, b.key(), (int) b.docCount()));
    }
    return facet;
  }

}
