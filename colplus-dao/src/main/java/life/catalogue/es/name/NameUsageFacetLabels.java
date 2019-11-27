package life.catalogue.es.name;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.es.response.Aggregations;
import life.catalogue.es.response.ContextFilterWrapper;
import life.catalogue.es.response.EsFacet;

import static life.catalogue.api.search.NameUsageSearchParameter.*;
import static life.catalogue.api.search.NameUsageSearchParameter.DECISION_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.FIELD;
import static life.catalogue.api.search.NameUsageSearchParameter.FOSSIL;
import static life.catalogue.api.search.NameUsageSearchParameter.ISSUE;
import static life.catalogue.api.search.NameUsageSearchParameter.NAME_ID;
import static life.catalogue.api.search.NameUsageSearchParameter.NAME_INDEX_ID;
import static life.catalogue.api.search.NameUsageSearchParameter.NOM_CODE;
import static life.catalogue.api.search.NameUsageSearchParameter.NOM_STATUS;
import static life.catalogue.api.search.NameUsageSearchParameter.PUBLISHED_IN_ID;
import static life.catalogue.api.search.NameUsageSearchParameter.PUBLISHER_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.RANK;
import static life.catalogue.api.search.NameUsageSearchParameter.RECENT;
import static life.catalogue.api.search.NameUsageSearchParameter.SECTOR_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.STATUS;
import static life.catalogue.api.search.NameUsageSearchParameter.TAXON_ID;
import static life.catalogue.api.search.NameUsageSearchParameter.TYPE;

/**
 * Determines and provides the labels to use for the various facets. We need to centralize this so that the query
 * generators use the same labels as the result parsers.
 */
public class NameUsageFacetLabels {

  static final String DATASET_KEY_FACET = "DATASET_KEY_FACET";
  static final String DECISION_KEY_FACET = "DECISION_KEY_FACET";
  static final String FIELD_FACET = "FIELD_FACET";
  static final String ISSUE_FACET = "ISSUE_FACET";
  static final String NAME_ID_FACET = "NAME_ID_FACET";
  static final String NAME_INDEX_ID_FACET = "NAME_INDEX_ID_FACET";
  static final String NOM_STATUS_FACET = "NOM_STATUS_FACET";
  static final String PUBLISHED_IN_ID_FACET = "PUBLISHED_IN_ID_FACET";
  static final String PUBLISHER_KEY_FACET = "PUBLISHER_KEY_FACET";
  static final String RANK_FACET = "RANK_FACET";
  static final String SECTOR_KEY_FACET = "SECTOR_KEY_FACET";
  static final String STATUS_FACET = "STATUS_FACET";
  static final String TAXON_ID_FACET = "TAXON_ID_FACET";
  static final String TYPE_FACET = "TYPE_FACET";
  static final String USAGE_ID_FACET = "USAGE_ID_FACET";
  static final String NOM_CODE_FACET = "NOM_CODE_FACET";
  static final String FOSSIL_FACET = "FOSSIL_FACET";
  static final String RECENT_FACET = "RECENT_FACET";

  /*
   * Maps NameSearchParameters to the labels to be used in aggregation queries
   */
  private static final EnumMap<NameUsageSearchParameter, String> facetLabels;

  static {

    facetLabels = new EnumMap<>(NameUsageSearchParameter.class);
    facetLabels.put(DATASET_KEY, DATASET_KEY_FACET);
    facetLabels.put(DECISION_KEY, DECISION_KEY_FACET);
    facetLabels.put(FIELD, FIELD_FACET);
    facetLabels.put(ISSUE, ISSUE_FACET);
    facetLabels.put(NAME_ID, NAME_ID_FACET);
    facetLabels.put(NAME_INDEX_ID, NAME_INDEX_ID_FACET);
    facetLabels.put(NOM_STATUS, NOM_STATUS_FACET);
    facetLabels.put(PUBLISHED_IN_ID, PUBLISHED_IN_ID_FACET);
    facetLabels.put(PUBLISHER_KEY, PUBLISHER_KEY_FACET);
    facetLabels.put(RANK, RANK_FACET);
    facetLabels.put(SECTOR_KEY, SECTOR_KEY_FACET);
    facetLabels.put(STATUS, STATUS_FACET);
    facetLabels.put(TAXON_ID, TAXON_ID_FACET);
    facetLabels.put(TYPE, TYPE_FACET);
    facetLabels.put(USAGE_ID, USAGE_ID_FACET);
    facetLabels.put(NOM_CODE, NOM_CODE_FACET);
    facetLabels.put(FOSSIL, FOSSIL_FACET);
    facetLabels.put(RECENT, RECENT_FACET);

    if (facetLabels.size() != NameUsageSearchParameter.values().length) {
      Set<NameUsageSearchParameter> all = new HashSet<NameUsageSearchParameter>(Arrays.asList(NameUsageSearchParameter.values()));
      all.removeAll(facetLabels.keySet());
      String unmapped = all.stream().map(Enum::name).collect(Collectors.joining(", "));
      String msg = "Some name search parameters not mapped to aggregation labels: " + unmapped;
      throw new IllegalStateException(msg);
    }

  }

  public static String getFacetLabel(NameUsageSearchParameter param) {
    return facetLabels.get(param);
  }

  public static String getContextLabel() {
    return Aggregations.LABEL;
  }

  public static String getContextFilterLabel() {
    return ContextFilterWrapper.LABEL;
  }

  public static String getBucketsLabel() {
    return EsFacet.LABEL;
  }

}
