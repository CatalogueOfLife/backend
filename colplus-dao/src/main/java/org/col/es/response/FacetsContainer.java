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
 * This will be the "aggregations" object in the ES query response if we used the SimpleFacetsTranslator
 */
public class FacetsContainer {

  @JsonProperty("doc_count")
  private int docCount;

  @JsonProperty(DATASET_KEY_FACET)
  private Facet datasetKey;

  @JsonProperty(RANK_FACET)
  private Facet rank;

  @JsonProperty(NOM_STATUS_FACET)
  private Facet nomStatus;

  @JsonProperty(STATUS_FACET)
  private Facet status;

  @JsonProperty(ISSUE_FACET)
  private Facet issue;

  @JsonProperty(TYPE_FACET)
  private Facet type;

  @JsonProperty(FIELD_FACET)
  private Facet field;

  /*
   * Unlikely to become facets, but just to be complete:
   */

  @JsonProperty(NAME_ID_FACET)
  private Facet nameId;

  @JsonProperty(NAME_INDEX_ID_FACET)
  private Facet nameIndexId;

  @JsonProperty(PUBLISHED_IN_ID_FACET)
  private Facet publishedInId;

  public int getDocCount() {
    return docCount;
  }

  public void setDocCount(int docCount) {
    this.docCount = docCount;
  }

  public Facet getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(Facet datasetKey) {
    this.datasetKey = datasetKey;
  }

  public Facet getRank() {
    return rank;
  }

  public void setRank(Facet rank) {
    this.rank = rank;
  }

  public Facet getNomStatus() {
    return nomStatus;
  }

  public void setNomStatus(Facet nomStatus) {
    this.nomStatus = nomStatus;
  }

  public Facet getStatus() {
    return status;
  }

  public void setStatus(Facet status) {
    this.status = status;
  }

  public Facet getIssue() {
    return issue;
  }

  public void setIssue(Facet issue) {
    this.issue = issue;
  }

  public Facet getType() {
    return type;
  }

  public void setType(Facet type) {
    this.type = type;
  }

  public Facet getField() {
    return field;
  }

  public void setField(Facet field) {
    this.field = field;
  }

  public Facet getNameId() {
    return nameId;
  }

  public void setNameId(Facet nameId) {
    this.nameId = nameId;
  }

  public Facet getNameIndexId() {
    return nameIndexId;
  }

  public void setNameIndexId(Facet nameIndexId) {
    this.nameIndexId = nameIndexId;
  }

  public Facet getPublishedInId() {
    return publishedInId;
  }

  public void setPublishedInId(Facet publishedInId) {
    this.publishedInId = publishedInId;
  }

}
