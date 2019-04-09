package org.col.api.vocab;

public enum EqualityMode {
  
  /**
   * The names index ids are the same, thus allow for ascii folding, gender stemming and minimal epithet variations in the name (see SciNameNormalizer)
   */
  NAMES_INDEX,
  
  /**
   * The canonical names (after parsing) are the same, but authorship might be different or missing
   */
  CANONICAL,
  
  /**
   * The canonical names (after parsing) and the normalised authorship (removing punctuation, case insensitive) is the same
   */
  CANONICAL_WITH_AUTHORS
  
}
