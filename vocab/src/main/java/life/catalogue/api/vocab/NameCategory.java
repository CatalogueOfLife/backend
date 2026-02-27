package life.catalogue.api.vocab;

import org.gbif.nameparser.api.Rank;

public enum NameCategory {
  
  UNINOMIAL(null, Rank.INFRAGENERIC_NAME,
    "Name made from a single epithet only. All ranks above species aggregates."),
  
  BINOMIAL(Rank.SPECIES_AGGREGATE, Rank.SPECIES,
    "Name made from a single epithet only. All ranks above species aggregates."),
  
  TRINOMIAL(Rank.INFRASPECIFIC_NAME, Rank.STRAIN,
    "Name made from a single epithet only. All ranks above species aggregates.");


  private final Rank highest;
  private final Rank lowest;
  private final String description;

  NameCategory(Rank highest, Rank lowest, String description) {
    this.highest = highest;
    this.lowest = lowest;
    this.description = description;
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

  public String getDescription() {
    return description;
  }
}
