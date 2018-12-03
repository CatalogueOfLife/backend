package org.col.es.response;

import java.util.EnumMap;

import org.col.api.search.NameSearchParameter;

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

/**
 * Determines and provides the labels to use at various levels of the aggregation queries and corresponding result. We need to centralize
 * this so that the query generators use the same labels as the result parsers.
 */
public class AggregationLabelProvider {

  static final String DATASET_KEY_FACET = "DATASET_KEY_FACET";
  static final String FIELD_FACET = "FIELD_FACET";
  static final String ISSUE_FACET = "ISSUE_FACET";
  static final String NAME_ID_FACET = "NAME_ID_FACET";
  static final String NAME_INDEX_ID_FACET = "NAME_INDEX_ID_FACET";
  static final String NOM_STATUS_FACET = "NOM_STATUS_FACET";
  static final String PUBLISHED_IN_ID_FACET = "PUBLISHED_IN_ID_FACET";
  static final String RANK_FACET = "RANK_FACET";
  static final String STATUS_FACET = "STATUS_FACET";
  static final String TYPE_FACET = "TYPE_FACET";

  /**
   * The label for aggregations operating within a separate execution context
   */
  static final String CONTEXT = "_CONTEXT_";
  /**
   * The label for aggregations operating within a separate execution context with a new set of filters applied.
   */
  static final String CONTEXT_FILTER = "_FILTER_";
  /**
   * The label for the inner-most object within the ES name search response, containing a facet value and its document count.
   */
  static final String BUCKETS = "BUCKETS";

  private static final EnumMap<NameSearchParameter, String> facetLabels;

  static {
    facetLabels = new EnumMap<>(NameSearchParameter.class);
    facetLabels.put(DATASET_KEY, DATASET_KEY_FACET);
    facetLabels.put(FIELD, FIELD_FACET);
    facetLabels.put(ISSUE, ISSUE_FACET);
    facetLabels.put(NAME_ID, NAME_ID_FACET);
    facetLabels.put(NAME_INDEX_ID, NAME_INDEX_ID_FACET);
    facetLabels.put(NOM_STATUS, NOM_STATUS_FACET);
    facetLabels.put(PUBLISHED_IN_ID, PUBLISHED_IN_ID_FACET);
    facetLabels.put(RANK, RANK_FACET);
    facetLabels.put(STATUS, STATUS_FACET);
    facetLabels.put(TYPE, TYPE_FACET);
  }

  public static String getFacetLabel(NameSearchParameter param) {
    return facetLabels.get(param);
  }

  public static String getContextLabel() {
    return CONTEXT;
  }

  public static String getContextFilterLabel() {
    return CONTEXT_FILTER;
  }

  public static String getBucketsLabel() {
    return BUCKETS;
  }

}
