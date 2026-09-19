package life.catalogue.matching;

import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.ArchivedNameUsageMapper;
import life.catalogue.db.mapper.ArchivedNameUsageMatchMapper;
import life.catalogue.matching.nidx.NameIndex;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rematches the entire name usage archive, truncating any previously existing matches first.
 */
public class ArchiveMatcher extends BaseMatcher {
  private static final Logger LOG = LoggerFactory.getLogger(ArchiveMatcher.class);

  public ArchiveMatcher(SqlSessionFactory factory, NameIndex ni) {
    super(factory, ni);
  }

  public void match() {
    LOG.warn("Rematch entire name usage archive, truncating all existing matches");
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(ArchivedNameUsageMatchMapper.class).truncate();
    }

    try (SqlSession readOnlySession = factory.openSession(true)) {
      var amum = readOnlySession.getMapper(ArchivedNameUsageMapper.class);
      try (BulkMatchHandler hn = new BulkMatchHandler(true, ArchivedNameUsageMatchMapper.class, false)) {
        PgUtils.consume(() -> amum.processArchivedNames(null, false), hn);
      }
    } catch (RuntimeException e) {
      LOG.error("Failed to rematch archive", e);
      throw e;

    } finally {
      LOG.info("Created {} name matches for {} archived names and {} not matching", updated, total, nomatch);
    }
  }

  /**
   * Rematches the archived names of one project through the names index. Only changed matches are written, and an
   * archived name that no longer matches loses its match record. Other projects are left alone.
   */
  public void match(int projectKey) {
    LOG.info("Rematch the name usage archive of project {}", projectKey);
    try (SqlSession readOnlySession = factory.openSession(true)) {
      var amum = readOnlySession.getMapper(ArchivedNameUsageMapper.class);
      try (BulkMatchHandler hn = new BulkMatchHandler(true, ArchivedNameUsageMatchMapper.class, true)) {
        PgUtils.consume(() -> amum.processArchivedNames(projectKey, false), hn);
      }
    } finally {
      LOG.info("Rematched {} archived names of project {}: {} changed, {} without a match", total, projectKey, updated, nomatch);
    }
  }
}
