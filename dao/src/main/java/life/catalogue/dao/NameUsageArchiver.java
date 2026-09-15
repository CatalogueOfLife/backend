package life.catalogue.dao;

import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.mapper.ArchivedNameUsageMapper;
import life.catalogue.db.mapper.ArchivedNameUsageMatchMapper;
import life.catalogue.db.mapper.DatasetMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * Builds and maintains the name usage archive of projects: one record per id any public release of the project
 * carried, holding the version of the highest ranked release that carries it, see {@link ReleaseRanking} and
 * docs/2026-09-15-name-usage-archive-migration.md.
 *
 * Everything goes through one per release step, {@link #archiveRelease(ReleaseRanking, int, boolean, boolean)}, used by
 * publishing, by the ArchiveRefreshJob and by building an empty archive. It only writes what is missing or different, so
 * running it twice, even at the same time, writes nothing the second time - both apps of a blue-green deploy receive the
 * publish event. It writes the release key last and in one statement, so a release whose key appears in the archive has
 * been archived completely, which {@link #unarchivedReleases(int)} relies on.
 *
 * Archive records are never deleted: an id only deleted releases carried could never be found again otherwise.
 */
public class NameUsageArchiver {
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageArchiver.class);
  private final SqlSessionFactory factory;
  private final IntFunction<IntSet> ignoredReleases;

  /**
   * An archiver that ignores no release, e.g. for tests.
   */
  public NameUsageArchiver(SqlSessionFactory factory) {
    this(factory, projectKey -> new IntOpenHashSet());
  }

  /**
   * @param ignoredReleases the release keys a project's release configs ignore, by project key
   */
  public NameUsageArchiver(SqlSessionFactory factory, IntFunction<IntSet> ignoredReleases) {
    this.factory = factory;
    this.ignoredReleases = ignoredReleases;
  }

  public ReleaseRanking ranking(int projectKey) {
    try (SqlSession session = factory.openSession(true)) {
      var releases = session.getMapper(DatasetMapper.class).listReleasesForArchive(projectKey);
      return new ReleaseRanking(projectKey, releases, ignoredReleases.apply(projectKey));
    }
  }

  /**
   * Builds the archive of every project from its releases.
   * @param truncate if true deletes the entire archive first, which loses every id only deleted releases carried
   */
  public void rebuildAll(boolean truncate) {
    List<Integer> projects;
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      var req = new DatasetSearchRequest();
      req.setOrigin(List.of(DatasetOrigin.PROJECT));
      req.setSortBy(DatasetSearchRequest.SortBy.KEY);
      projects = dm.searchKeys(req, Users.SUPERUSER);
      if (truncate) {
        LOG.warn("Truncate entire name usage archive");
        session.getMapper(ArchivedNameUsageMatchMapper.class).truncate();
        session.getMapper(ArchivedNameUsageMapper.class).truncate();
      }
    }

    LOG.info("Total number of projects found to rebuild: {}", projects.size());
    for (var key : projects) {
      try {
        rebuildProject(key, true);
      } catch (Exception e) {
        LOG.error("Failed to archive names for project {}", key, e);
      }
    }
  }

  /**
   * Builds the archive of a project whose archive is empty, from the releases that still exist.
   * @param copyMatches if true also copies the names index matches of the release usages
   * @throws IllegalArgumentException if the key is not a project or its archive already contains usages
   */
  public ArchiveStats rebuildProject(int projectKey, boolean copyMatches) {
    try (SqlSession session = factory.openSession(true)) {
      var project = session.getMapper(DatasetMapper.class).get(projectKey);
      if (project.getOrigin() != DatasetOrigin.PROJECT) {
        throw new IllegalArgumentException("Dataset " + projectKey + " is not a project");
      }
      if (project.hasDeletedDate()) {
        throw new IllegalArgumentException("Project " + projectKey + " is deleted");
      }
      int count = session.getMapper(ArchivedNameUsageMapper.class).count(projectKey);
      if (count > 0) {
        throw new IllegalArgumentException("Project " + projectKey + " already contains " + count + " archived name usages");
      }
    }
    return archiveProject(ranking(projectKey), copyMatches, false);
  }

  /**
   * Runs the per release step for every public, not deleted release of the project, highest rank first, so a record is
   * rewritten at most once, by its best release. Then tidies the release keys of all records of the project.
   *
   * The given ranking decides the order only. A refresh runs for hours and nothing stops a release from being published
   * or deleted meanwhile, so every release is ranked again right before it is archived, and skipped if it is no longer
   * archivable.
   */
  public ArchiveStats archiveProject(ReleaseRanking ranking, boolean copyMatches, boolean dryRun) {
    final int projectKey = ranking.getProjectKey();
    LOG.info("{} the name usage archive of project {} from its releases, highest rank first:\n{}",
      dryRun ? "Counting what would change in" : "Refreshing", projectKey, ranking.describe());
    var total = new ArchiveStats();
    for (var r : ranking.archivable()) {
      final var fresh = ranking(projectKey);
      if (fresh.archivable().stream().noneMatch(x -> x.getKey() == r.getKey())) {
        LOG.warn("Release {} of project {} is no longer archivable, skip it", r.getKey(), projectKey);
        continue;
      }
      total.add(archiveRelease(fresh, r.getKey(), copyMatches, dryRun));
    }
    if (!dryRun) {
      try (SqlSession session = factory.openSession(true)) {
        int tidied = session.getMapper(ArchivedNameUsageMapper.class).tidyReleaseKeys(projectKey);
        LOG.info("Sorted and de-duplicated the release keys of {} archived usages of project {}", tidied, projectKey);
      }
    }
    LOG.info("{} name usage archive of project {}: {}", dryRun ? "Dry run of the" : "Refreshed", projectKey, total);
    return total;
  }

  /**
   * Archives a published release, ranked against all releases of its project.
   */
  public ArchiveStats archiveRelease(int releaseKey) {
    var info = DatasetInfoCache.CACHE.info(releaseKey);
    if (!info.origin.isRelease()) {
      throw new IllegalArgumentException("Not a release " + releaseKey);
    }
    return archiveRelease(ranking(info.sourceKey), releaseKey, true, false);
  }

  /**
   * The per release step, see the class docs.
   * @param copyMatches if true copies the names index matches of the release usages for the records it holds the version of
   * @param dryRun if true only counts what the step would write, each release against the archive as it is now
   */
  public ArchiveStats archiveRelease(ReleaseRanking ranking, int releaseKey, boolean copyMatches, boolean dryRun) {
    if (!ranking.isArchivable(releaseKey)) {
      throw new IllegalArgumentException("Release " + releaseKey + " is private or deleted and cannot be archived");
    }
    final int projectKey = ranking.getProjectKey();
    final boolean supplies = ranking.supplies(releaseKey);
    final List<Integer> blocking = ranking.blockingKeys(releaseKey);
    final ArchiveStats stats = new ArchiveStats();
    stats.releases = 1;
    try (SqlSession session = factory.openSession(true)) {
      var anum = session.getMapper(ArchivedNameUsageMapper.class);
      if (dryRun) {
        stats.inserted = anum.countMissingUsages(projectKey, releaseKey);
        if (supplies) {
          stats.rewritten = anum.countOutdatedUsages(projectKey, releaseKey, blocking, false);
          stats.renamed = anum.countOutdatedUsages(projectKey, releaseKey, blocking, true);
        }
        stats.keysAdded = anum.countMissingReleaseKeys(projectKey, releaseKey);

      } else {
        stats.inserted = anum.createMissingUsages(projectKey, releaseKey);
        if (supplies) {
          stats.rewritten = anum.updateExistingUsages(projectKey, releaseKey, blocking);
        }
        if (copyMatches) {
          var amm = session.getMapper(ArchivedNameUsageMatchMapper.class);
          stats.matchesCopied = amm.copyReleaseMatches(projectKey, releaseKey, blocking, !supplies);
          stats.matchesDeleted = amm.deleteUnmatchedReleaseMatches(projectKey, releaseKey, blocking, !supplies);
        }
        // redirects are decided by the newest generation, the staged pairs of any other release are stale
        if (supplies && ranking.isNewestGeneration(releaseKey)) {
          stats.supersededCleared = anum.clearSuperseded(projectKey, releaseKey);
        }
        if (ranking.decidesRedirects(releaseKey)) {
          // an id another supplying release of the newest generation carries is live
          var liveIn = ranking.newestGenerationSupplyingKeys().stream().filter(k -> k != releaseKey).toList();
          stats.supersededApplied = anum.applySuperseded(projectKey, releaseKey, liveIn);
        }
        anum.deleteSuperseded(releaseKey);
        // last: a release key in the archive means the release was archived completely
        stats.keysAdded = anum.addReleaseKey(projectKey, releaseKey);
      }
    }
    LOG.info("{} release {} of project {}: {}", dryRun ? "Dry run of archiving" : "Archived", releaseKey, projectKey, stats);
    return stats;
  }

  /**
   * @return the public, not deleted releases of the project that have usages but whose key appears nowhere in the
   *   archive, i.e. which were never, or not completely, archived
   */
  public List<Integer> unarchivedReleases(int projectKey) {
    List<Integer> missing = new ArrayList<>();
    try (SqlSession session = factory.openSession(true)) {
      var anum = session.getMapper(ArchivedNameUsageMapper.class);
      for (var r : session.getMapper(DatasetMapper.class).listReleasesForArchive(projectKey)) {
        if (r.isArchivable() && !anum.isReleaseArchived(projectKey, r.getKey()) && anum.hasUsages(r.getKey())) {
          missing.add(r.getKey());
        }
      }
    }
    return missing;
  }
}
