package org.col.api.model;

/**
 * Entity with an ID property scoped within a single dataset.
 */
public interface DatasetEntity {
  
  String getId();
  
  void setId(String id);

  Integer getDatasetKey();
  
  void setDatasetKey(Integer key);

}
