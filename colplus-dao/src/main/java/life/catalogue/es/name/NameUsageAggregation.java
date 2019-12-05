package life.catalogue.es.name;

import com.fasterxml.jackson.annotation.JsonProperty;

import life.catalogue.es.response.EsFacet;
import life.catalogue.es.response.Aggregation;

import static life.catalogue.es.name.NameUsageFacetLabels.DATASET_KEY_FACET;
import static life.catalogue.es.name.NameUsageFacetLabels.*;
import static life.catalogue.es.name.NameUsageFacetLabels.FIELD_FACET;
import static life.catalogue.es.name.NameUsageFacetLabels.ISSUE_FACET;
import static life.catalogue.es.name.NameUsageFacetLabels.NAME_ID_FACET;
import static life.catalogue.es.name.NameUsageFacetLabels.NAME_INDEX_ID_FACET;
import static life.catalogue.es.name.NameUsageFacetLabels.NOM_STATUS_FACET;
import static life.catalogue.es.name.NameUsageFacetLabels.PUBLISHED_IN_ID_FACET;
import static life.catalogue.es.name.NameUsageFacetLabels.PUBLISHER_KEY_FACET;
import static life.catalogue.es.name.NameUsageFacetLabels.RANK_FACET;
import static life.catalogue.es.name.NameUsageFacetLabels.SECTOR_KEY_FACET;
import static life.catalogue.es.name.NameUsageFacetLabels.STATUS_FACET;
import static life.catalogue.es.name.NameUsageFacetLabels.TAXON_ID_FACET;
import static life.catalogue.es.name.NameUsageFacetLabels.TYPE_FACET;

public class NameUsageAggregation extends Aggregation {

  @JsonProperty(DATASET_KEY_FACET)
  private EsFacet datasetKey;

  @JsonProperty(CATALOGUE_KEY_FACET)
  private EsFacet catalogueKey;

  @JsonProperty(DECISION_MODE_FACET)
  private EsFacet decisionMode;

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

  public EsFacet getCatalogueKey() {
    return catalogueKey;
  }

  public EsFacet getDecisionMode() {
    return decisionMode;
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
