package org.col.es.name.search;

import java.util.EnumMap;

import org.col.api.search.NameSearchParameter;
import org.col.es.response.AggregationResult;
import org.col.es.response.ContextFilterWrapper;
import org.col.es.response.EsFacet;

import static org.col.api.search.NameSearchParameter.DATASET_KEY;
import static org.col.api.search.NameSearchParameter.DECISION_KEY;
import static org.col.api.search.NameSearchParameter.FIELD;
import static org.col.api.search.NameSearchParameter.ISSUE;
import static org.col.api.search.NameSearchParameter.NAME_ID;
import static org.col.api.search.NameSearchParameter.NAME_INDEX_ID;
import static org.col.api.search.NameSearchParameter.NOM_STATUS;
import static org.col.api.search.NameSearchParameter.PUBLISHED_IN_ID;
import static org.col.api.search.NameSearchParameter.PUBLISHER_KEY;
import static org.col.api.search.NameSearchParameter.RANK;
import static org.col.api.search.NameSearchParameter.SECTOR_KEY;
import static org.col.api.search.NameSearchParameter.STATUS;
import static org.col.api.search.NameSearchParameter.TAXON_ID;
import static org.col.api.search.NameSearchParameter.TYPE;

/**
 * Determines and provides the labels to use at various levels of the aggregation queries and the result objects coming back from them. We
 * need to centralize this so that the query generators use the same labels as the result parsers.
 */
public class NameFacetLabels {

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

  /*
   * Maps NameSearchParameters to labels to be used in aggregation queries
   */
  private static final EnumMap<NameSearchParameter, String> facetLabels;

  static {
    facetLabels = new EnumMap<>(NameSearchParameter.class);
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
    if (facetLabels.size() != NameSearchParameter.values().length) {
      throw new IllegalStateException("Not all name search parameters mapped to aggregation labels");
    }
  }

  public static String getFacetLabel(NameSearchParameter param) {
    return facetLabels.get(param);
  }

  public static String getContextLabel() {
    return AggregationResult.LABEL;
  }

  public static String getContextFilterLabel() {
    return ContextFilterWrapper.LABEL;
  }

  public static String getBucketsLabel() {
    return EsFacet.LABEL;
  }

}
