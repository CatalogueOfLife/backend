package life.catalogue.matching.nidx;

import life.catalogue.common.Managed;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * A pure {@code normalized-String -> nidx-int} registry backing the names index.
 * The single-tier, canonical-only index holds no full row instances - it only maps a normalized
 * canonical bucket key to the names index id. Full rows (see
 * {@link life.catalogue.api.model.NameIndexEntry}) are looked up on demand from postgres.
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
   * DateTime the store was first created or entirely cleared. Persisted with the store, so it survives
   * a restart and only moves when the index itself is rebuilt.
   */
  LocalDateTime created();

  /**
   * A unique id regenerated whenever the store is created from scratch or entirely cleared, and otherwise
   * stable across restarts. Prefer this over {@link #created()} to tell one index apart from another: it is
   * an exact equality check with no precision, formatting or timezone hazard, while the timestamp remains
   * the thing to look at when the question is which of two indexes is the newer one.
   */
  UUID id();
}
