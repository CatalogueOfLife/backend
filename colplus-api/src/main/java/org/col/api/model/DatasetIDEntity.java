package org.col.api.model;

/**
 * Entity with an ID property scoped within a single dataset.
 */
public interface DatasetIDEntity extends DatasetEntity{
  
  String getId();
  
  void setId(String id);

}
