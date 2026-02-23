package life.catalogue.dao;

import life.catalogue.api.vocab.Users;
import life.catalogue.es2.indexing.NameUsageIndexService;
import life.catalogue.junit.MybatisTestUtils;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.matching.nidx.NameIndexFactory;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.Test;

public class VerbatimDaoTest extends DaoTestBase {

  VerbatimDao dao;

  @Before
  public void init(){
    dao = new VerbatimDao(factory());
  }

  @Test
  public void orphans() {
    dao.deleteOrphans(testDataRule.testData.key, Users.TESTER);
  }
}