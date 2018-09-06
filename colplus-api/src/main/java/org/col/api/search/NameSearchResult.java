package org.col.api.search;

public class NameSearchResult {

  /**
   * Primary key of the name as given in the dataset dwc:scientificNameID. Only guaranteed to be
   * unique within a dataset and can follow any kind of schema.
   */
  private String id;
  
  /**
   * Key to dataset instance. Defines context of the name key.
   */
  private Integer datasetKey;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Integer getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }

}
