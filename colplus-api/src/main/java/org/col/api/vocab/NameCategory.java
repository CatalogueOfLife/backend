package org.col.api.vocab;

import org.gbif.nameparser.api.Rank;

public enum NameCategory {
  
  /**
   * Name made from a single epithet only.
   * All ranks above species aggregates.
   */
  UNINOMIAL(null, Rank.INFRAGENERIC_NAME),
  
  /**
   * Name made from a single epithet only.
   * All ranks above species aggregates.
   */
  BINOMIAL(Rank.SPECIES_AGGREGATE, Rank.SPECIES),
  
  /**
   * Name made from a single epithet only.
   * All ranks above species aggregates.
   */
  TRINOMIAL(Rank.INFRASPECIFIC_NAME, Rank.STRAIN);
  
  private final Rank highest;
  private final Rank lowest;
  
  NameCategory(Rank highest, Rank lowest) {
    this.highest = highest;
    this.lowest = lowest;
  }
  
  /**
   * Highest, inclusive rank to be considered for the category.
   * Can be null if open ended.
   */
  public Rank getHighest() {
    return highest;
  }
  
  /**
   * Lowest, inclusive rank to be considered for the category.
   * Can be null if open ended.
   */
  public Rank getLowest() {
    return lowest;
  }
}
