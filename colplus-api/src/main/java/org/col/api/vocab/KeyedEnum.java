package org.col.api.vocab;

/**
 * Marks an enum as having a manually managed key attributes which should be used for persistence in postgres instead of the volatile ordinal.
 */
public interface KeyedEnum {
  
  int getKey();
  
}
