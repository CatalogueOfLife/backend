package life.catalogue.dao;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.mapper.DatasetMapper.ArchivableRelease;

import java.time.LocalDateTime;
import java.util.*;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * Ranks the releases of one project for the name usage archive, which keeps every id at the version of the highest
 * ranked release carrying it. See docs/2026-09-15-name-usage-archive-migration.md.
 *
 * An extended release belongs to the generation of the base release it was built on. Generations are ordered by their
 * base release's attempt, newest first. Within one generation the base release ranks above its extended releases, and
 * a newer extended release above an older one.
 *
 * The base release of an extended release is the one its job recorded as params.baseReleaseKey. Where that is missing
 * it is the newest base release that is not private, was not deleted before the extended release was created, and was
 * created at least one full day before it. Dataset notes are never read, other projects may write them differently.
 */
public class ReleaseRanking {
  private final int projectKey;
  private final Map<Integer, ArchivableRelease> byKey = new HashMap<>();
  private final Int2IntMap baseOf = new Int2IntOpenHashMap();
  private final IntSet fallbackBase = new IntOpenHashSet();
  private final IntSet ignored;
  private final List<ArchivableRelease> ranked;

  /**
   * @param releases every release of the project, private and deleted ones included, but no temporary datasets
   * @param ignored the release keys the project's release configs ignore
   */
  public ReleaseRanking(int projectKey, List<ArchivableRelease> releases, IntSet ignored) {
    this.projectKey = projectKey;
    this.ignored = ignored;
    releases.forEach(r -> byKey.put(r.getKey(), r));
    for (var r : releases) {
      if (r.getOrigin() == DatasetOrigin.XRELEASE) {
        var recorded = r.getBaseReleaseKey() == null ? null : byKey.get(r.getBaseReleaseKey());
        if (recorded != null && recorded.getOrigin() == DatasetOrigin.RELEASE) {
          baseOf.put(r.getKey(), recorded.getKey());
        } else {
          var fallback = fallbackBase(r, releases);
          if (fallback != null) {
            baseOf.put(r.getKey(), fallback.intValue());
            fallbackBase.add(r.getKey());
          }
        }
      }
    }
    ranked = releases.stream().sorted(this::compare).toList();
  }

  @Nullable
  private static Integer fallbackBase(ArchivableRelease xr, List<ArchivableRelease> releases) {
    if (xr.getCreated() == null) {
      return null;
    }
    final LocalDateTime latest = xr.getCreated().minusDays(1);
    return releases.stream()
      .filter(r -> r.getOrigin() == DatasetOrigin.RELEASE && !r.isPrivat() && r.getCreated() != null)
      .filter(r -> r.getDeleted() == null || r.getDeleted().isAfter(xr.getCreated()))
      .filter(r -> !r.getCreated().isAfter(latest))
      .max(Comparator.comparing(ArchivableRelease::getCreated).thenComparingInt(ArchivableRelease::getKey))
      .map(ArchivableRelease::getKey)
      .orElse(null);
  }

  /**
   * @return the attempt of the base release a release belongs to, MIN_VALUE for an extended release without any
   */
  private int generation(ArchivableRelease r) {
    if (r.getOrigin() == DatasetOrigin.XRELEASE) {
      return baseOf.containsKey(r.getKey()) ? attempt(byKey.get(baseOf.get(r.getKey()))) : Integer.MIN_VALUE;
    }
    return attempt(r);
  }

  private static int attempt(ArchivableRelease r) {
    return r.getAttempt() == null ? 0 : r.getAttempt();
  }

  /**
   * Highest rank first.
   */
  private int compare(ArchivableRelease a, ArchivableRelease b) {
    int c = Integer.compare(generation(b), generation(a));
    if (c != 0) return c;
    c = Boolean.compare(a.getOrigin() == DatasetOrigin.XRELEASE, b.getOrigin() == DatasetOrigin.XRELEASE);
    if (c != 0) return c;
    c = Integer.compare(attempt(b), attempt(a));
    if (c != 0) return c;
    return Integer.compare(b.getKey(), a.getKey());
  }

  private ArchivableRelease require(int releaseKey) {
    var r = byKey.get(releaseKey);
    if (r == null) {
      throw new IllegalArgumentException("Dataset " + releaseKey + " is no release of project " + projectKey);
    }
    return r;
  }

  public int getProjectKey() {
    return projectKey;
  }

  /**
   * @return the public, not deleted releases, highest rank first
   */
  public List<ArchivableRelease> archivable() {
    return ranked.stream().filter(ArchivableRelease::isArchivable).toList();
  }

  public boolean isArchivable(int releaseKey) {
    return require(releaseKey).isArchivable();
  }

  /**
   * @return true if the release is archivable and no release config ignores it, so its data may be an archived version
   */
  public boolean supplies(int releaseKey) {
    return require(releaseKey).isArchivable() && !ignored.contains(releaseKey);
  }

  /**
   * @return the keys of the supplying releases whose archived versions the given release must not overwrite:
   *   the ones ranked above it if it supplies versions itself, all of them if it does not
   */
  public List<Integer> blockingKeys(int releaseKey) {
    final boolean supplies = supplies(releaseKey);
    List<Integer> keys = new ArrayList<>();
    for (var r : ranked) {
      if (r.getKey() == releaseKey) {
        if (supplies) {
          break;
        }
      } else if (supplies(r.getKey())) {
        keys.add(r.getKey());
      }
    }
    return keys;
  }

  /**
   * @return the generation of the highest ranked archivable release, null if the project has none
   */
  @Nullable
  private Integer newestGeneration() {
    return ranked.stream()
      .filter(ArchivableRelease::isArchivable)
      .findFirst()
      .map(this::generation)
      .orElse(null);
  }

  /**
   * @return true if the release belongs to the newest generation, the generation of the highest ranked archivable release
   */
  public boolean isNewestGeneration(int releaseKey) {
    var r = require(releaseKey);
    final Integer newest = newestGeneration();
    return newest != null && generation(r) == newest;
  }

  /**
   * @return the keys of the supplying releases of the newest generation, highest rank first
   */
  public List<Integer> newestGenerationSupplyingKeys() {
    final Integer newest = newestGeneration();
    if (newest == null) {
      return List.of();
    }
    return ranked.stream()
      .filter(r -> supplies(r.getKey()) && generation(r) == newest)
      .map(ArchivableRelease::getKey)
      .toList();
  }

  /**
   * Superseded redirects are decided by the newest generation: its highest ranked supplying base release and its highest
   * ranked supplying extended release each apply the pairs staged for them.
   * @return true if the release supplies versions, belongs to the newest generation and no supplying release of the same
   *   origin in the newest generation ranks above it
   */
  public boolean decidesRedirects(int releaseKey) {
    var release = require(releaseKey);
    if (!supplies(releaseKey) || !isNewestGeneration(releaseKey)) {
      return false;
    }
    for (int key : newestGenerationSupplyingKeys()) {
      if (byKey.get(key).getOrigin() == release.getOrigin()) {
        return key == releaseKey;
      }
    }
    return false;
  }

  @Nullable
  public Integer baseRelease(int xreleaseKey) {
    require(xreleaseKey);
    return baseOf.containsKey(xreleaseKey) ? baseOf.get(xreleaseKey) : null;
  }

  /**
   * @return true if the base release of the extended release was not recorded by its job and came from the fallback rule
   */
  public boolean isFallbackBase(int xreleaseKey) {
    require(xreleaseKey);
    return fallbackBase.contains(xreleaseKey);
  }

  /**
   * @return one line per release, highest rank first, for the job log
   */
  public String describe() {
    var sb = new StringBuilder();
    for (var r : ranked) {
      sb.append(r.getKey()).append(' ').append(r.getOrigin()).append(" attempt ").append(r.getAttempt());
      if (r.getOrigin() == DatasetOrigin.XRELEASE) {
        sb.append(", base ").append(baseRelease(r.getKey()));
        if (isFallbackBase(r.getKey())) {
          sb.append(" (fallback)");
        }
      }
      if (!r.isArchivable()) {
        sb.append(r.isPrivat() ? ", private" : ", deleted");
      } else if (!supplies(r.getKey())) {
        sb.append(", ignored");
      }
      sb.append('\n');
    }
    return sb.toString();
  }
}
