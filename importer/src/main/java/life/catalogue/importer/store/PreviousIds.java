package life.catalogue.importer.store;

import life.catalogue.common.text.StringUtils;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.NameUsageMapper.GeneratedUsage;

import org.gbif.nameparser.api.Rank;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

/**
 * The identifiers a previous import generated for records the source does not identify itself, so they can be
 * reapplied to the same records on the next import instead of being regenerated in a new, shifted order.
 * See <a href="https://github.com/CatalogueOfLife/backend/issues/1189">#1189</a>.
 * <p>
 * Records are keyed by rank, scientific name and authorship. When several previous records share that key,
 * e.g. homonymous genera in different families, the scientific name of the parent picks the right one and the
 * remaining candidates are handed out in a stable order. Every id is handed out at most once, so N previous
 * duplicates map onto N new records; which of them gets which id can swap, which we accept.
 * <p>
 * Instances are built once per import and are not thread safe - a single ImportStore owns one for its lifetime.
 */
public class PreviousIds {
  private static final Logger LOG = LoggerFactory.getLogger(PreviousIds.class);
  /** the instance used when there is no database or the previous version could not be read */
  public static final PreviousIds NONE = new PreviousIds(Map.of(), Map.of(), Set.of());

  /** the name and usage id a single record carried in the previous version of the dataset */
  public record IdPair(String usageId, String nameId) {}

  private record Candidate(IdPair ids, @Nullable String parentKey) {}

  private final Map<String, Deque<Candidate>> candidates;
  private final Map<String, String> nameIdByUsageId; // only populated when source usages are included
  private final Set<String> reserved;
  private int reused = 0;
  private int missed = 0;
  private int blocked = 0;

  @VisibleForTesting
  PreviousIds(Map<String, Deque<Candidate>> candidates, Map<String, String> nameIdByUsageId, Set<String> reserved) {
    this.candidates = candidates;
    this.nameIdByUsageId = nameIdByUsageId;
    this.reserved = reserved;
  }

  /**
   * Streams the previous version of the dataset, which postgres still holds until PgImport deletes it as its very
   * first statement. Must therefore be called before that, i.e. before or during normalization.
   * Never throws: if the previous ids cannot be read we simply generate new ones.
   *
   * @param factory       the session factory, null in tests without a database
   * @param includeSource if true also loads usages of origin SOURCE, needed for formats like TextTree that carry
   *                      no identifiers of their own
   */
  public static PreviousIds load(@Nullable SqlSessionFactory factory, int datasetKey, boolean includeSource) {
    if (factory == null) {
      return NONE;
    }
    try {
      var builder = new Builder(includeSource);
      try (var session = factory.openSession(true)) {
        PgUtils.consume(
          () -> session.getMapper(NameUsageMapper.class).processDatasetGeneratedUsages(datasetKey, includeSource),
          builder::add
        );
      }
      if (builder.isEmpty()) {
        return NONE;
      }
      var ids = builder.build();
      LOG.info("Loaded {} previously generated identifiers under {} distinct names for dataset {}",
        ids.size(), ids.candidates.size(), datasetKey);
      return ids;

    } catch (RuntimeException e) {
      LOG.error("Failed to read the previously generated identifiers of dataset {}. New ids will be generated", datasetKey, e);
      return NONE;
    }
  }

  /**
   * @param available tests whether both ids of a candidate are still free in the current import
   * @return the ids the same record carried in the previous version, or null if there is none left to hand out
   */
  public @Nullable IdPair take(@Nullable Rank rank, @Nullable String scientificName, @Nullable String authorship,
                               @Nullable String parentName, Predicate<IdPair> available) {
    if (candidates.isEmpty()) {
      // nothing was ever loaded - also keeps the shared NONE instance free of any state
      return null;
    }
    String k = key(rank, scientificName, authorship);
    if (k != null) {
      var queue = candidates.get(k);
      if (queue != null) {
        final String parentKey = norm(parentName);
        for (var c = poll(queue, parentKey); c != null; c = poll(queue, parentKey)) {
          if (available.test(c.ids())) {
            reused++;
            return c.ids();
          }
          // a record of this import already occupies one of the two ids
          blocked++;
        }
      }
    }
    missed++;
    return null;
  }

  /**
   * Removes and returns the candidate with the given parent, or the first one if the parent is unknown
   * or matches none of them.
   */
  private static @Nullable Candidate poll(Deque<Candidate> queue, @Nullable String parentKey) {
    if (parentKey != null && queue.size() > 1) {
      for (var c : queue) {
        if (parentKey.equals(c.parentKey())) {
          queue.remove(c);
          return c;
        }
      }
    }
    return queue.poll();
  }

  /**
   * @return the name id the usage with that very id had in the previous version.
   *         Only available when source usages were included, i.e. for TextTree.
   */
  public @Nullable String nameIdFor(@Nullable String usageId) {
    return usageId == null ? null : nameIdByUsageId.get(usageId);
  }

  /**
   * @return true if a previous import generated that id, so it must not be handed to a newly generated record
   *         before the record it belongs to had its chance to reclaim it
   */
  public boolean isReserved(@Nullable String id) {
    return id != null && reserved.contains(id);
  }

  public void report(int datasetKey) {
    if (reused > 0 || missed > 0) {
      LOG.info("Reapplied {} previously generated identifiers in dataset {}, generated {} new ones, {} were taken already",
        reused, datasetKey, missed, blocked);
    }
  }

  @VisibleForTesting
  static @Nullable String key(@Nullable Rank rank, @Nullable String scientificName, @Nullable String authorship) {
    String name = norm(scientificName);
    if (name == null) {
      return null;
    }
    return (rank == null ? Rank.UNRANKED : rank).name() + '|' + name + '|' + norm(authorship);
  }

  /** folds to ascii, uppercases and collapses all non alphanumerics - the same normalisation IdProvider uses */
  private static @Nullable String norm(@Nullable String x) {
    return StringUtils.digitOrAsciiLetters(x);
  }

  public int getReused() {
    return reused;
  }

  public int getMissed() {
    return missed;
  }

  public int getBlocked() {
    return blocked;
  }

  public int size() {
    return reserved.size();
  }

  /**
   * Builds an instance straight from usage rows, for tests that have no database.
   */
  @VisibleForTesting
  public static PreviousIds of(boolean includeSource, GeneratedUsage... usages) {
    var builder = new Builder(includeSource);
    for (var u : usages) {
      builder.add(u);
    }
    return builder.build();
  }

  /**
   * Accumulates rows and only orders the duplicates once at the end.
   */
  private static class Builder {
    private final boolean includeSource;
    private final Map<String, List<Candidate>> byKey = new HashMap<>();
    private final Map<String, String> nameIds = new HashMap<>();
    private final Set<String> reserved = new HashSet<>();

    Builder(boolean includeSource) {
      this.includeSource = includeSource;
    }

    void add(GeneratedUsage u) {
      // reserve unconditionally, also for rows we cannot key - a new id must never take one of these
      reserved.add(u.usageId);
      if (u.nameId != null) {
        reserved.add(u.nameId);
      }
      if (includeSource) {
        nameIds.put(u.usageId, u.nameId);
      }
      String k = key(u.rank, u.scientificName, u.authorship);
      if (k != null && u.nameId != null) {
        byKey.computeIfAbsent(k, x -> new ArrayList<>()).add(new Candidate(new IdPair(u.usageId, u.nameId), norm(u.parent)));
      }
    }

    boolean isEmpty() {
      return reserved.isEmpty();
    }

    PreviousIds build() {
      // ids are issued incrementally, so the smallest id is the oldest - keep that order to stay stable
      final Map<String, Deque<Candidate>> sorted = new HashMap<>(byKey.size());
      for (var e : byKey.entrySet()) {
        e.getValue().sort(Comparator.comparing(c -> c.ids().usageId()));
        sorted.put(e.getKey(), new ArrayDeque<>(e.getValue()));
      }
      return new PreviousIds(sorted, nameIds, reserved);
    }
  }
}
