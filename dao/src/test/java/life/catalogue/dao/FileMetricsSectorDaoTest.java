package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SimpleNameLink;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.SectorMapper;

import java.sql.Statement;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;

public class FileMetricsSectorDaoTest extends FileMetricsDaoTestBase<DSID<Integer>> {

  @Before
  public void initDao() throws Exception {
    dao = new FileMetricsSectorDao(factory(), treeRepoRule.getRepo());
    // need to update all usages to belong to the sector, the test data has NULL everywhere
    try (SqlSession session = factory().openSession(true);
         Statement st = session.getConnection().createStatement()
    ) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      Sector s = new Sector();
      s.setDatasetKey(11);
      s.setSubjectDatasetKey(11);
      s.setSubject(SimpleNameLink.of("t1", "t1", "", null));
      s.setTarget(SimpleNameLink.of("t1", "t1", "", null));
      s.applyUser(TestDataRule.TEST_USER);
      sm.create(s);
      session.commit();
      key = DSID.copy(s.getKey());

      st.execute("UPDATE name SET sector_key = " + key.getId() + " WHERE dataset_key=" + key.getDatasetKey());
      st.execute("UPDATE name_usage SET sector_key = " + key.getId() + " WHERE dataset_key=" + key.getDatasetKey());
    }
  }

}
