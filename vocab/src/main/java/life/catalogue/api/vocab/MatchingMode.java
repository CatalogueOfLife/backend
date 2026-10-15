package life.catalogue.api.vocab;

/**
 * How names are compared, by the duplicate search and by name matching.
 */
public enum MatchingMode {

  /**
   * Name matching falls back to a higher rank taxon of the classification if the name itself cannot be matched.
   * The duplicate search compares names exactly, as in STRICT.
   */
  DEFAULT,

  /**
   * Strict mode with exact matching. Name matching never falls back to a higher rank taxon.
   */
  STRICT,

  /**
   * Fuzzy mode that allows for slight fuzzy matching,
   * e.g. ascii folding, gender stemming and minimal epithet variations in the name (see SciNameNormalizer).
   * Not yet available for name matching.
   */
  FUZZY;

  /**
   * @return true if name matching falls back to a higher rank taxon when the name itself cannot be matched
   */
  public boolean higherRank() {
    return this != STRICT;
  }
}
