package org.col.es.translate;

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

  public static final String DATASET_KEY_FACET = "DATASET_KEY_FACET";
  public static final String FIELD_FACET = "FIELD_FACET";
  public static final String ISSUE_FACET = "ISSUE_FACET";
  public static final String NAME_ID_FACET = "NAME_ID_FACET";
  public static final String NAME_INDEX_ID_FACET = "NAME_INDEX_ID_FACET";
  public static final String NOM_STATUS_FACET = "NOM_STATUS_FACET";
  public static final String PUBLISHED_IN_ID_FACET = "PUBLISHED_IN_ID_FACET";
  public static final String RANK_FACET = "RANK_FACET";
  public static final String STATUS_FACET = "STATUS_FACET";
  public static final String TYPE_FACET = "TYPE_FACET";

  /**
   * The label for aggregations operating within a separate execution context
   */
  public static final String CONTEXT = "_CONTEXT_";
  /**
   * The label for aggregations operating within a separate execution context with a new set of filters applied.
   */
  public static final String CONTEXT_FILTER = "_FILTER_";

  public static final String BUCKETS = "BUCKETS";

  private static final EnumMap<NameSearchParameter, String> map1;

  static {
    map1 = new EnumMap<>(NameSearchParameter.class);
    map1.put(DATASET_KEY, DATASET_KEY_FACET);
    map1.put(FIELD, FIELD_FACET);
    map1.put(ISSUE, ISSUE_FACET);
    map1.put(NAME_ID, NAME_ID_FACET);
    map1.put(NAME_INDEX_ID, NAME_INDEX_ID_FACET);
    map1.put(NOM_STATUS, NOM_STATUS_FACET);
    map1.put(PUBLISHED_IN_ID, PUBLISHED_IN_ID_FACET);
    map1.put(RANK, RANK_FACET);
    map1.put(STATUS, STATUS_FACET);
    map1.put(TYPE, TYPE_FACET);
  }

  public static String getFacetLabel(NameSearchParameter param) {
    return map1.get(param);
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
