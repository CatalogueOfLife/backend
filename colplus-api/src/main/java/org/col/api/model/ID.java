package org.col.api.model;

/**
 * Entity that has a String based ID as its primary key,
 * unique only within a dataset
 */
public interface ID {
  
  String getId();
  
  void setId(String id);
  
}
