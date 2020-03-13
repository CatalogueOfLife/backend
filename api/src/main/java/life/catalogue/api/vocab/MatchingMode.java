package life.catalogue.api.vocab;

public enum MatchingMode {
  
  /**
   * Strict mode with exact matching
   */
  STRICT,
  
  /**
   * Fuzzy mode that allows for slight fuzzy matching,
   * e.g. ascii folding, gender stemming and minimal epithet variations in the name (see SciNameNormalizer)
   */
  FUZZY
  
}
