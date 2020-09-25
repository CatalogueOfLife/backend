package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SimpleNameLink;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.SectorMapper;
import org.apache.ibatis.session.SqlSession;
import org.junit.Before;

import java.sql.Statement;

public class FileMetricsSectorDaoTest extends FileMetricsDaoTestBase<DSID<Integer>> {

  static final int sectorKey = 1;

  @Before
  public void initDao() throws Exception {
    dao = new FileMetricsSectorDao(factory(), treeRepoRule.getRepo());
    key = DSID.of(11, sectorKey);
    // need to update all usages to belong to the sector, the test data has NULL everywhere
    try (SqlSession session = factory().openSession(true);
         Statement st = session.getConnection().createStatement()
    ) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      Sector s = new Sector();
      s.setKey(key);
      s.setSubjectDatasetKey(11);
      s.setSubject(SimpleNameLink.of("t1", "t1", "", null));
      s.setTarget(SimpleNameLink.of("t1", "t1", "", null));
      s.applyUser(TestDataRule.TEST_USER);
      sm.create(s);

      st.execute("UPDATE name_11 SET sector_key = " + sectorKey);
      st.execute("UPDATE name_usage_11 SET sector_key = " + sectorKey);
    }
  }

}
