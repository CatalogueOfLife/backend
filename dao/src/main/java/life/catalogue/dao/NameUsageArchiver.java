package life.catalogue.dao;

import life.catalogue.api.model.DatasetRelease;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.mapper.ArchivedNameUsageMapper;
import life.catalogue.db.mapper.ArchivedNameUsageMatchMapper;
import life.catalogue.db.mapper.DatasetMapper;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service that builds the name usage archive for projects.
 * All name usages of all public releases will be included in the archive.
 *
 * An id is archived once, when the release that minted it is published, and every later release it survives adds its
 * key to release_keys and refreshes the archived copy of the name. The archive therefore holds every id this project
 * ever issued, each as it looked in the *latest* release that carried it - which for a deleted id is the last release
 * it was still in. That is what the release id mapping scores the next release against.
 *
 * If you want to rebuild an existing archive please manually delete the existing archive records first.
 * This guarantees that no existing archive is deleted or overwritten accidently by this tool.
 */
public class NameUsageArchiver {
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageArchiver.class);
  private final SqlSessionFactory factory;

  public NameUsageArchiver(SqlSessionFactory factory) {
    this.factory = factory;
  }

  /**
   *
   * @param truncate if true deletes the name usage archive before rebuilding
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
      LOG.info("Rebuild name usage archive for project {}", key);
      rebuildProject(key, false);
    }

    LOG.info("Copy all name matches for all archived projects");
    try (SqlSession session = factory.openSession(true)) {
      var amm = session.getMapper(ArchivedNameUsageMatchMapper.class);
      var matches = amm.createAllMatches();
      LOG.info("Copied {} name matches", matches);
    }
  }

  /**
   * Rebuilds the name usage archive for a given project if it does not yet exist.
   * If a single archived record exists already an IAE will be thrown.
   *
   * The rebuild uses only the currently existing, non deleted releases to decide which usages will have to be archived.
   * @param copyMatches if true also copies the existing name matches for the newly created archive records
   * @throws IllegalArgumentException if the project key is not a project or the archive already contains usages
   */
  public void rebuildProject(int projectKey, boolean copyMatches) {
    List<DatasetRelease> releases;
    try (SqlSession session = factory.openSession(true)) {
      var dm = session.getMapper(DatasetMapper.class);
      var project = dm.get(projectKey);
      if (project.getOrigin() != DatasetOrigin.PROJECT) {
        throw new IllegalArgumentException("Dataset "+ projectKey+" is not a project");
      }
      if (project.hasDeletedDate()) {
        throw new IllegalArgumentException("Project "+ projectKey+" is deleted");
      }
      int count = session.getMapper(ArchivedNameUsageMapper.class).count(projectKey);
      if (count > 0) {
        throw new IllegalArgumentException("Project "+projectKey+" already contains "+count+" archived name usages");
      }
      // finally allow the rebuild for each release
      releases = dm.listReleasesQuick(projectKey, false, false);
    }

    try {
      LOG.info("Archiving name usages for {} public releases of PROJECT {}", releases.size(), projectKey);
      int archived = 0;
      for (var d : releases) {
        archived += archiveRelease(d.getKey(), copyMatches);
      }
      LOG.info("Archived {} name usages for all {} releases of project {}", archived, releases.size(), projectKey);

    } catch (Exception e) {
      LOG.error("Failed to archive names for project {}", projectKey, e);
    }
  }

  /**
   * Creates new and updates existing archived usages according to the usages from the releaseKey.
   * The release is required to be public, otherwise an IAE is thrown.
   * @param releaseKey valid release key - not verified, must not be deleted or private!
   * @param copyMatches if true also copies the existing name matches for the newly created archive records
   * @return number of newly created archived usages
   */
  public int archiveRelease(int releaseKey, boolean copyMatches) throws RuntimeException {
    var info = DatasetInfoCache.CACHE.info(releaseKey);
    if (!info.origin.isRelease()) {
      throw new IllegalArgumentException("Not a release " + releaseKey);
    }

    int created = 0;
    final int projectKey = info.sourceKey;
    try (SqlSession session = factory.openSession(true)) {
      var dm = session.getMapper(DatasetMapper.class);
      var release = dm.get(releaseKey);
      if (release.isPrivat()) {
        throw new IllegalArgumentException("Release " + releaseKey+ " has not been published yet");
      }

      LOG.info("Updating names archive for project {} with release {}", projectKey, releaseKey);

      var anum = session.getMapper(ArchivedNameUsageMapper.class);
      LOG.info("Adding release key of all archive records which also exist in release {} of project {}", releaseKey, projectKey);
      int updated = anum.addReleaseKey(projectKey, releaseKey);
      LOG.info("Updated {} archive records which exist in release {} of project {}", updated, releaseKey, projectKey);

      // the archive is the memory the next release scores its ids against, so it has to hold what a name looks like
      // now, not what it looked like when its id was first issued
      LOG.info("Refreshing changed archive records from release {} of project {}", releaseKey, projectKey);
      int refreshed = anum.updateExistingUsages(projectKey, releaseKey);
      LOG.info("Refreshed {} changed archive records from release {} of project {}", refreshed, releaseKey, projectKey);

      LOG.info("Creating missing archive records from release {} of project {}", releaseKey, projectKey);
      created = anum.createMissingUsages(projectKey, releaseKey);
      LOG.info("Created {} new archive records from release {} of project {}", created, releaseKey, projectKey);

      if (copyMatches) {
        var anumm = session.getMapper(ArchivedNameUsageMatchMapper.class);
        LOG.info("Copy missing archive matches from release {} of project {}", releaseKey, projectKey);
        var matches = anumm.createMissingMatches(projectKey, releaseKey);
        LOG.info("Copied {} archive matches from release {} of project {}", matches, releaseKey, projectKey);
        // a refreshed name usually sits in a different names index bucket - the archive match has to follow it there
        if (refreshed > 0) {
          var repointed = anumm.refreshMatches(projectKey, releaseKey);
          LOG.info("Re-pointed {} archive matches of changed names from release {} of project {}", repointed, releaseKey, projectKey);
        }
      }
    }
    return created;
  }

}
