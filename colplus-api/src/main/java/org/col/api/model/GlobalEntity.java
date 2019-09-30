package org.col.api.model;

/**
 * Entity that is keyed on a simple integer globally unique and not tight to a dataset scope.
 */
public interface GlobalEntity {
  
  Integer getKey();
  
  void setKey(Integer key);
}
