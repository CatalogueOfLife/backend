package org.col.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import static org.col.es.response.AggregationLabelProvider.DATASET_KEY_FACET;
import static org.col.es.response.AggregationLabelProvider.DECISION_KEY_FACET;
import static org.col.es.response.AggregationLabelProvider.FIELD_FACET;
import static org.col.es.response.AggregationLabelProvider.ISSUE_FACET;
import static org.col.es.response.AggregationLabelProvider.NAME_ID_FACET;
import static org.col.es.response.AggregationLabelProvider.NAME_INDEX_ID_FACET;
import static org.col.es.response.AggregationLabelProvider.NOM_STATUS_FACET;
import static org.col.es.response.AggregationLabelProvider.PUBLISHED_IN_ID_FACET;
import static org.col.es.response.AggregationLabelProvider.PUBLISHER_KEY_FACET;
import static org.col.es.response.AggregationLabelProvider.RANK_FACET;
import static org.col.es.response.AggregationLabelProvider.SECTOR_KEY_FACET;
import static org.col.es.response.AggregationLabelProvider.STATUS_FACET;
import static org.col.es.response.AggregationLabelProvider.TAXON_ID_FACET;
import static org.col.es.response.AggregationLabelProvider.TYPE_FACET;

/**
 * The data structure within the ES response within which we will find the facets.
 */
public class EsFacetsContainer {

  @JsonProperty("doc_count")
  private int docCount;

  @JsonProperty(DATASET_KEY_FACET)
  private EsFacet datasetKey;

  @JsonProperty(DECISION_KEY_FACET)
  private EsFacet decisionKey;

  @JsonProperty(FIELD_FACET)
  private EsFacet field;

  @JsonProperty(ISSUE_FACET)
  private EsFacet issue;

  @JsonProperty(NAME_ID_FACET)
  private EsFacet nameId;

  @JsonProperty(NAME_INDEX_ID_FACET)
  private EsFacet nameIndexId;

  @JsonProperty(NOM_STATUS_FACET)
  private EsFacet nomStatus;

  @JsonProperty(PUBLISHED_IN_ID_FACET)
  private EsFacet publishedInId;

  @JsonProperty(PUBLISHER_KEY_FACET)
  private EsFacet publisherKey;

  @JsonProperty(RANK_FACET)
  private EsFacet rank;

  @JsonProperty(SECTOR_KEY_FACET)
  private EsFacet sectorKey;

  @JsonProperty(STATUS_FACET)
  private EsFacet status;

  @JsonProperty(TAXON_ID_FACET)
  private EsFacet taxonId;

  @JsonProperty(TYPE_FACET)
  private EsFacet type;

  public int getDocCount() {
    return docCount;
  }

  public EsFacet getDatasetKey() {
    return datasetKey;
  }

  public EsFacet getDecisionKey() {
    return decisionKey;
  }

  public EsFacet getField() {
    return field;
  }

  public EsFacet getIssue() {
    return issue;
  }

  public EsFacet getNameId() {
    return nameId;
  }

  public EsFacet getNameIndexId() {
    return nameIndexId;
  }

  public EsFacet getNomStatus() {
    return nomStatus;
  }

  public EsFacet getPublishedInId() {
    return publishedInId;
  }

  public EsFacet getPublisherKey() {
    return publisherKey;
  }

  public EsFacet getRank() {
    return rank;
  }

  public EsFacet getSectorKey() {
    return sectorKey;
  }

  public EsFacet getStatus() {
    return status;
  }

  public EsFacet getTaxonId() {
    return taxonId;
  }

  public EsFacet getType() {
    return type;
  }

}
