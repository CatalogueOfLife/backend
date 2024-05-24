package life.catalogue.dao;

import life.catalogue.api.model.ArchivedNameUsage;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.IdReportType;
import life.catalogue.common.id.IdConverter;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.*;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service that builds the name usage archive for projects based on their existing id_reports.
 * It requires an empty archive. If you want to rebuild an existing archive please manually delete the existing archive records first.
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
   * If a single archived record exists already an IAE wil be thrown.
   *
   * The rebuild expects the id reports to be correct and makes use of them to decide which usages will have to be archived.
   */
  public void rebuildProject(int projectKey) {
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
      var datasets = dm.listReleases(projectKey);
      int archived = 0;
      LOG.info("Archiving name usages for {} releases of project {}", datasets.size(), projectKey);
      for (var d : datasets) {
        if (d.isPrivat()) {
          LOG.info("Ignore private release {}", d.getKey());
        } else if (d.hasDeletedDate()) {
          LOG.info("Ignore deleted release {}", d.getKey());
        } else {
          archived += archiveRelease(projectKey, d.getKey());
        }
      }
      LOG.info("Archived {} name usages for project {}", archived, projectKey);
    }
  }

  /**
   * Creates and removes archived usages according to the existing id reports.
   * @param projectKey valid project key - not verified, must be existing
   * @param releaseKey valid release key - not verified, must not be deleted or private!
   * @return change of number of archived usages, i.e. newly archived - resurrected ones
   */
  public int archiveRelease(int projectKey, int releaseKey) {
    final AtomicInteger counter = new AtomicInteger(0);
    final AtomicInteger delCounter = new AtomicInteger(0);
    try (SqlSession session = factory.openSession(true);
         SqlSession batchSession = factory.openSession(ExecutorType.BATCH, false)
    ) {
      if (!session.getMapper(NameMapper.class).hasData(releaseKey)) {
        LOG.info("Release {} has id reports, but no data to archive", releaseKey);
        return 0;
      }

      var idm = session.getMapper(IdReportMapper.class);
      var tm = session.getMapper(TaxonMapper.class);
      var rm = session.getMapper(ReferenceMapper.class);
      var num = session.getMapper(NameUsageMapper.class);
      var anm = batchSession.getMapper(ArchivedNameUsageMapper.class);
      Integer previousKey = session.getMapper(DatasetMapper.class).previousRelease(releaseKey);
      if (previousKey == null) {
        LOG.info("Ignoring first release {} from project {}", releaseKey, projectKey);
        return 0;
      }

      LOG.info("Rebuilding names archive from id reports for release {} from project {} with previous release {}", releaseKey, projectKey, previousKey);
      final DSID<String> archiveKey = DSID.root(projectKey);
      PgUtils.consume(() -> idm.processDataset(releaseKey), r -> {
        if (r.getType() != IdReportType.CREATED) {
          final String id = IdConverter.LATIN29.encode(r.getId());

          if (r.getType() == IdReportType.RESURRECTED) {
            anm.delete(archiveKey.id(id));
            delCounter.incrementAndGet();

          } else if (r.getType() == IdReportType.DELETED) {
            var oldKey = DSID.of(previousKey, id);
            var u = num.get(oldKey);
            if (u == null) {
              LOG.warn("Cannot archive missing name usage {}, deleted from dataset {}", oldKey, releaseKey);
              return;
            }
            // assemble archived usage
            ArchivedNameUsage au = new ArchivedNameUsage(u);
            // archived usages belong to project, not release!
            au.setDatasetKey(projectKey);
            // basionym
            var bas = NameDao.getBasionym(factory, oldKey);
            if (bas != null) {
              au.setBasionym(new SimpleName(bas));
            }
            // publishedIn
            if (u.getName().getPublishedInId() != null) {
              var pub = rm.get(DSID.of(previousKey, u.getName().getPublishedInId()));
              if (pub != null) {
                au.setPublishedIn(pub.getCitation());
              }
            }
            // classification
            au.setClassification(tm.classificationSimple(oldKey));
            // lastReleaseKey
            au.setLastReleaseKey(previousKey);
            // firstReleaseKey
            var first = idm.first(projectKey, r.getId());
            if (first == null) {
              LOG.warn("Deleted ID {} missing created IdReport event", r.getId());
            } else {
              au.setFirstReleaseKey(first.getDatasetKey());
            }

            if (u.isTaxon()){
              // extinct
              Taxon t = (Taxon) u;
              au.setExtinct(t.isExtinct());

            } else if (u.isBareName()) {
              LOG.warn("{} stable ID {} is a {}. Skip", r.getType(), id, u.getStatus());
              return;
            }

            anm.create(au);
            if (counter.incrementAndGet() % 5000 == 0) {
              batchSession.commit();
            }
          }
        }
      });
      batchSession.commit();
      LOG.info("Copied {} name usages into the project archive {} as their stable IDs were deleted in release {}.", counter, projectKey, releaseKey);
      LOG.info("Deleted {} resurrected name usages from the project archive {}.", delCounter, projectKey);
    }
    return counter.get() - delCounter.get();
  }

}
