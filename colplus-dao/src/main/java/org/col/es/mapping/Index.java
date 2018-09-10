package org.col.es.mapping;

/**
 * Enumerates the three possible values for the &#34;index&#34; field in a type mapping.
F */
public enum Index {
  
  ANALYZED, NOT_ANALYZED, NO;

  @Override
  public String toString() {
    return name().toLowerCase();
  }
}
