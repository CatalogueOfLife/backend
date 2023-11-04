package life.catalogue.matching;

import life.catalogue.api.model.DSID;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.ArchivedNameUsageMapper;
import life.catalogue.db.mapper.ArchivedNameUsageMatchMapper;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameMatchMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rematches dataset sectors, using 2 separate db connections for read & write
 */
public class SectorMatcher extends BaseMatcher {
  private static final Logger LOG = LoggerFactory.getLogger(SectorMatcher.class);
  private int sectors = 0;

  public SectorMatcher(SqlSessionFactory factory, NameIndex ni) {
    super(factory, ni);
  }

  /**
   * Matches all names of a dataset sector and updates its name index id and issues in postgres
   *
   * @param allowInserts if true allows inserts into the names index
   */
  public void match(DSID<Integer> sectorKey, boolean allowInserts) throws RuntimeException {
    final int totalBefore = total;
    final int updatedBefore = updated;
    final int nomatchBefore = nomatch;

    try (SqlSession session = factory.openSession(false);
         BulkMatchHandler hn = new BulkMatchHandlerNames(sectorKey.getDatasetKey(), allowInserts);
    ) {
      LOG.info("Create name matches for sector {}", sectorKey);
      NameMapper nm = session.getMapper(NameMapper.class);
      PgUtils.consume(() -> nm.processSector(sectorKey), hn);
    } catch (RuntimeException e) {
      LOG.error("Failed to rematch sector {}", sectorKey, e);
      throw e;
    } finally {
      sectors++;
      LOG.info("Created {} name matches for {} names and {} not matching for sector {}", updated - updatedBefore, total - totalBefore, nomatch - nomatchBefore, sectorKey);
    }
  }

  public int getSectors() {
    return sectors;
  }

}
