package org.col.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import static org.col.es.response.AggregationLabelProvider.DATASET_KEY_FACET;
import static org.col.es.response.AggregationLabelProvider.FIELD_FACET;
import static org.col.es.response.AggregationLabelProvider.ISSUE_FACET;
import static org.col.es.response.AggregationLabelProvider.NAME_ID_FACET;
import static org.col.es.response.AggregationLabelProvider.NAME_INDEX_ID_FACET;
import static org.col.es.response.AggregationLabelProvider.NOM_STATUS_FACET;
import static org.col.es.response.AggregationLabelProvider.PUBLISHED_IN_ID_FACET;
import static org.col.es.response.AggregationLabelProvider.RANK_FACET;
import static org.col.es.response.AggregationLabelProvider.STATUS_FACET;
import static org.col.es.response.AggregationLabelProvider.TYPE_FACET;

/**
 * The data structure within the ES response within which we will find the facets.
 */
public class EsFacetsContainer {

  @JsonProperty("doc_count")
  private int docCount;

  @JsonProperty(DATASET_KEY_FACET)
  private EsFacet datasetKey;

  @JsonProperty(RANK_FACET)
  private EsFacet rank;

  @JsonProperty(NOM_STATUS_FACET)
  private EsFacet nomStatus;

  @JsonProperty(STATUS_FACET)
  private EsFacet status;

  @JsonProperty(ISSUE_FACET)
  private EsFacet issue;

  @JsonProperty(TYPE_FACET)
  private EsFacet type;

  @JsonProperty(FIELD_FACET)
  private EsFacet field;

  /*
   * Unlikely to become facets, but just to be complete:
   */

  @JsonProperty(NAME_ID_FACET)
  private EsFacet nameId;

  @JsonProperty(NAME_INDEX_ID_FACET)
  private EsFacet nameIndexId;

  @JsonProperty(PUBLISHED_IN_ID_FACET)
  private EsFacet publishedInId;

  public int getDocCount() {
    return docCount;
  }

  public EsFacet getDatasetKey() {
    return datasetKey;
  }

  public EsFacet getRank() {
    return rank;
  }

  public EsFacet getNomStatus() {
    return nomStatus;
  }

  public EsFacet getStatus() {
    return status;
  }

  public EsFacet getIssue() {
    return issue;
  }

  public EsFacet getType() {
    return type;
  }

  public EsFacet getField() {
    return field;
  }

  public EsFacet getNameId() {
    return nameId;
  }

  public EsFacet getNameIndexId() {
    return nameIndexId;
  }

  public EsFacet getPublishedInId() {
    return publishedInId;
  }

}
