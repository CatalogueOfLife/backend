package org.col.api.model;

/**
 * Verbatim entity that also has a String based ID as its primary key.
 */
public interface ID {
  
  String getId();
  
  void setId(String id);
  
}
