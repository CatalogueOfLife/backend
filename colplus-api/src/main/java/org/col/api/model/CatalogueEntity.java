package org.col.api.model;

/**
 * Global entity that is keyed on a simple, globally unique integer but which is in addition
 * scoped to a datasetKey
 */
public interface CatalogueEntity extends GlobalEntity {
  
  /**
   * @return the catalogues datasetKey
   */
  Integer getDatasetKey();
  
  /**
   * Sets the catalogues datasetKey
   */
  void setDatasetKey(Integer key);
}
