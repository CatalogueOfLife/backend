package org.col.api.model;

import java.util.Set;

/**
 *
 */
public interface Referenced {
  Set<Integer> getReferenceKeys();

  void setReferenceKeys(Set<Integer> referenceKeys);

  void addReferenceKey(Integer referenceKey);

}
