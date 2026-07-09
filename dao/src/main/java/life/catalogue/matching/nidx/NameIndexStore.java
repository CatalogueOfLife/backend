package life.catalogue.matching.nidx;

import life.catalogue.common.Managed;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * A pure {@code normalized-String -> nidx-int} registry backing the names index.
 * The single-tier, canonical-only index no longer holds {@link life.catalogue.api.model.IndexName}
 * instances - it only maps a normalized canonical bucket key to the names index id.
 */
public interface NameIndexStore extends Managed {

  /**
   * @param normalized the normalized canonical bucket key
   * @return the nidx for the key, or 0 if absent
   */
  int get(String normalized);

  void add(String normalized, int nidx);

  boolean contains(String normalized);

  /**
   * @return the number of entries held. Potentially an expensive operation.
   */
  int count();

  /**
   * The maximum nidx of all stored entries.
   * @return max nidx or zero if store is empty
   */
  int maxKey();

  /**
   * Remove all entries of the names index store.
   */
  void clear();

  /**
   * @return an iterable over all held entries (normalized key -> nidx).
   */
  Iterable<Map.Entry<String, Integer>> entries();

  /**
   * Tries to compact the store, but retaining all identifiers.
   */
  void compact();

  /**
   * DateTime the store was first created or entirely cleared.
   */
  LocalDateTime created();
}
