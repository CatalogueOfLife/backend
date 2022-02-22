package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.IdReportType;
import life.catalogue.common.id.IdConverter;
import life.catalogue.db.mapper.*;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchivedNameUsageFactory {
  private static final Logger LOG = LoggerFactory.getLogger(ArchivedNameUsageFactory.class);
  private final SqlSessionFactory factory;

  public ArchivedNameUsageFactory(SqlSessionFactory factory) {
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
      if (project.getOrigin() != DatasetOrigin.MANAGED) {
        throw new IllegalArgumentException("Dataset "+ projectKey+" is not a project");
      }
      if (project.hasDeletedDate()) {
        throw new IllegalArgumentException("Project "+ projectKey+" is deleted");
      }
      int count = session.getMapper(ArchivedNameMapper.class).count(projectKey);
      if (count > 0) {
        throw new IllegalArgumentException("Project "+projectKey+" already contains "+count+" archived name usages");
      }
      // finally allow the rebuild for each release
      for (var d : dm.listReleases(projectKey)) {
        buildRelease(projectKey, d.getKey());
      }
    }
  }

  /**
   * Creates and removes archived usages according to the existing id reports.
   * @param projectKey valid project key - not verified
   * @param releaseKey valid release key - not verified
   * @return number of archived usages
   */
  public int buildRelease(int projectKey, int releaseKey) {
    int counter = 0;
    int delCounter = 0;
    try (SqlSession session = factory.openSession(true);
         SqlSession batchSession = factory.openSession(ExecutorType.BATCH, false)
    ) {
      var idm = session.getMapper(IdReportMapper.class);
      var tm = session.getMapper(TaxonMapper.class);
      var rm = session.getMapper(ReferenceMapper.class);
      var num = session.getMapper(NameUsageMapper.class);
      var anm = batchSession.getMapper(ArchivedNameMapper.class);
      Integer previousKey = session.getMapper(DatasetMapper.class).previousRelease(releaseKey);
      LOG.info("Rebuilding names archive from id reports for release {} from project {} with previous release {}", releaseKey, projectKey, previousKey);

      final DSID<String> archiveKey = DSID.root(projectKey);
      for (IdReportEntry r : idm.processDataset(releaseKey)) {
        if (r.getType() != IdReportType.CREATED) {
          final String id = IdConverter.LATIN29.encode(r.getId());

          if (r.getType() == IdReportType.RESURRECTED) {
            anm.delete(archiveKey.id(id));
            delCounter++;

          } else if (r.getType() == IdReportType.DELETED) {
            var oldKey = DSID.of(previousKey, id);
            var u = num.get(oldKey);
            // assemble archived usage
            ArchivedNameUsage au = new ArchivedNameUsage(u);
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
              continue;
            }

            anm.create(au);
            if (counter++ % 5000 == 0) {
              batchSession.commit();
            }
          }
        }
      }
      batchSession.commit();
      LOG.info("Copied {} name usages into the project archive {} as their stable IDs were deleted in release {}.", counter, projectKey, releaseKey);
      LOG.info("Deleted {} resurrected name usages from the project archive {}.", delCounter, projectKey);
    }
    return counter;
  }

}
