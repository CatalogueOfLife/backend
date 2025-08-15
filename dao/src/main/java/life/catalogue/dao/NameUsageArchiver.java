package life.catalogue.dao;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.mapper.ArchivedNameUsageMapper;
import life.catalogue.db.mapper.ArchivedNameUsageMatchMapper;
import life.catalogue.db.mapper.DatasetMapper;

import java.util.stream.Collectors;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service that builds the name usage archive for projects.
 * All name usages of all public releases will be included in the archive.
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
   * Rebuilds the name usage archive for a given project if it does not yet exist.
   * If a single archived record exists already an IAE will be thrown.
   *
   * The rebuild uses only the currently existing, non deleted releases to decide which usages will have to be archived.
   * @param copyMatches if true also copies the existing name matches for the newly created archive records
   */
  public void rebuildProject(int projectKey, boolean copyMatches) {
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
      var datasets = dm.listReleasesQuick(projectKey);
      var releases = datasets.stream().filter(d -> !d.isDeleted() && !d.isPrivat()).collect(Collectors.toList());
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
      LOG.info("Updating last release key of all archive records which still exist in release {} of project {}", releaseKey, projectKey);
      int updated = anum.addReleaseKey(projectKey, releaseKey);
      LOG.info("Updated {} archive records which still exist in release {} of project {}", updated, releaseKey, projectKey);

      LOG.info("Copy missing archive records from release {} of project {}", releaseKey, projectKey);
      created = anum.createMissingUsages(projectKey, releaseKey);
      LOG.info("Copied {} new archive records from release {} of project {}", created, releaseKey, projectKey);

      if (copyMatches) {
        LOG.info("Copy missing archive matches from release {} of project {}", releaseKey, projectKey);
        var anumm = session.getMapper(ArchivedNameUsageMatchMapper.class);
        var matches = anumm.createMissingMatches(projectKey, releaseKey);
        LOG.info("Copied {} archive matches from release {} of project {}", matches, releaseKey, projectKey);
      }
    }
    return created;
  }

}
