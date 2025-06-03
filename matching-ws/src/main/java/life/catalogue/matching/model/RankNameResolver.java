package life.catalogue.matching.model;

import org.gbif.nameparser.api.Rank;

/**
 * An interface for resolving the name of a rank.
 */
public interface RankNameResolver {

  /**
   * Returns the name for a given rank.
   *
   * @param rank the rank to resolve
   * @return the name of the rank, or null if no name is defined for this rank
   */
  String nameFor(Rank rank);
}
