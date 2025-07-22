package life.catalogue.matching;

import life.catalogue.api.event.DatasetDataChanged;
import life.catalogue.concurrent.GlobalBlockingJob;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.ArchivedNameUsageMapper;
import life.catalogue.db.mapper.ArchivedNameUsageMatchMapper;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameMatchMapper;
import life.catalogue.event.EventBroker;
import life.catalogue.matching.nidx.NameIndex;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Matches all names & archived names missing any name match incl NoMatch.
 */
public class GlobalMatcherJob extends GlobalBlockingJob {
  private final GlobalMatcher gm;
  public GlobalMatcherJob(int userKey, SqlSessionFactory factory, NameIndex ni, EventBroker bus) {
    super(userKey, JobPriority.HIGH);
    this.gm = new GlobalMatcher(factory, ni, bus);
  }

  @Override
  public void execute() throws Exception {
    gm.run();
  }

  static class GlobalMatcher extends BaseMatcher implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalMatcher.class);
    private final EventBroker bus;

    GlobalMatcher(SqlSessionFactory factory, NameIndex ni, @Nullable EventBroker bus) {
      super(factory, ni);
      this.bus = bus;
    }

    @Override
    public void run() throws RuntimeException {
      LOG.info("Create missing name matches for all datasets");
      try (SqlSession readOnlySession = factory.openSession(true);
           BulkMatchHandler hn = new BulkMatchHandler(true, NameMatchMapper.class, false);
      ) {
        var mapper = readOnlySession.getMapper(NameMapper.class);
        PgUtils.consume(() -> mapper.processDatasetWithoutMatches(null), hn);
        LOG.info("Created {} missing name matches, {} not matching.", total, nomatch);
        if (bus != null) {
          for (int dkey : hn.getDatasets()) {
            bus.publish(new DatasetDataChanged(dkey));
          }
        }
      } catch (RuntimeException e) {
        LOG.error("Failed to rematch missing name matches", e);
        throw e;
      }

      LOG.info("Create missing archived name matches for all project");
      try (SqlSession readOnlySession = factory.openSession(true);
           BulkMatchHandler hn = new BulkMatchHandler(true, ArchivedNameUsageMatchMapper.class, false);
      ) {
        total = 0;
        nomatch = 0;
        var mapper = readOnlySession.getMapper(ArchivedNameUsageMapper.class);
        PgUtils.consume(() -> mapper.processArchivedNames(null, true), hn);
        LOG.info("Created {} missing archived name matches, {} not matching.", total, nomatch);
      } catch (RuntimeException e) {
        LOG.error("Failed to rematch missing archived name matches", e);
        throw e;
      }
    }
  }

}