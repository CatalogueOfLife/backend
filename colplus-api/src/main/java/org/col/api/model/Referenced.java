package org.col.api.model;

import java.util.Set;

/**
 *
 */
public interface Referenced {
  Set<String> getReferenceIds();
  
  void setReferenceIds(Set<String> referenceIds);
  
  void addReferenceId(String referenceId);
  
}
