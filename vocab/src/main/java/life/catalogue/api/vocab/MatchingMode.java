package life.catalogue.api.vocab;

/**
 * How names are compared, by the duplicate search and by name matching.
 * Name matching of external queries defaults to RELAXED, the duplicate search to STRICT.
 * Merges never use a mode, they always match strictly.
 */
public enum MatchingMode {

  /**
   * Name matching falls back to a higher rank taxon of the classification if the name itself cannot be matched,
   * and treats a conflicting taxonomic group of the classification as a preference rather than a veto:
   * a usage of a compatible group still wins, but a usage of another group is no longer ruled out if the query is a
   * bi- or trinomial classified only by a broad group like a kingdom, or if the two groups overlap, e.g. protists filed
   * under animals. A different authorship always rules a usage out.
   * A rank given apart from an infraspecific name without a rank marker is a preference too: a usage of that rank still
   * wins, but a usage of another infraspecific rank is no longer ruled out.
   * The duplicate search compares names exactly, as in STRICT.
   */
  RELAXED,

  /**
   * Strict mode with exact matching. Name matching never falls back to a higher rank taxon, and a taxonomic group of the
   * classification that conflicts with the one of a usage rules the usage out.
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

  /**
   * @return true if name matching relaxes the taxonomic group veto
   */
  public boolean relaxedGroups() {
    return this != STRICT;
  }

  /**
   * @return true if name matching takes a rank given apart from an infraspecific name without a rank marker as a preference
   */
  public boolean relaxedRanks() {
    return this != STRICT;
  }
}
