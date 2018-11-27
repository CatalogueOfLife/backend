package org.col.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import static org.col.es.translate.AggregationLabelProvider.DATASET_KEY_FACET;
import static org.col.es.translate.AggregationLabelProvider.FIELD_FACET;
import static org.col.es.translate.AggregationLabelProvider.ISSUE_FACET;
import static org.col.es.translate.AggregationLabelProvider.NAME_ID_FACET;
import static org.col.es.translate.AggregationLabelProvider.NAME_INDEX_ID_FACET;
import static org.col.es.translate.AggregationLabelProvider.NOM_STATUS_FACET;
import static org.col.es.translate.AggregationLabelProvider.PUBLISHED_IN_ID_FACET;
import static org.col.es.translate.AggregationLabelProvider.RANK_FACET;
import static org.col.es.translate.AggregationLabelProvider.STATUS_FACET;
import static org.col.es.translate.AggregationLabelProvider.TYPE_FACET;

/**
 * The data structure within the ES response within which we will find the facets.
 */
public class EsFacets {

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

  public void setDocCount(int docCount) {
    this.docCount = docCount;
  }

  public EsFacet getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(EsFacet datasetKey) {
    this.datasetKey = datasetKey;
  }

  public EsFacet getRank() {
    return rank;
  }

  public void setRank(EsFacet rank) {
    this.rank = rank;
  }

  public EsFacet getNomStatus() {
    return nomStatus;
  }

  public void setNomStatus(EsFacet nomStatus) {
    this.nomStatus = nomStatus;
  }

  public EsFacet getStatus() {
    return status;
  }

  public void setStatus(EsFacet status) {
    this.status = status;
  }

  public EsFacet getIssue() {
    return issue;
  }

  public void setIssue(EsFacet issue) {
    this.issue = issue;
  }

  public EsFacet getType() {
    return type;
  }

  public void setType(EsFacet type) {
    this.type = type;
  }

  public EsFacet getField() {
    return field;
  }

  public void setField(EsFacet field) {
    this.field = field;
  }

  public EsFacet getNameId() {
    return nameId;
  }

  public void setNameId(EsFacet nameId) {
    this.nameId = nameId;
  }

  public EsFacet getNameIndexId() {
    return nameIndexId;
  }

  public void setNameIndexId(EsFacet nameIndexId) {
    this.nameIndexId = nameIndexId;
  }

  public EsFacet getPublishedInId() {
    return publishedInId;
  }

  public void setPublishedInId(EsFacet publishedInId) {
    this.publishedInId = publishedInId;
  }

}
