package life.catalogue.dao;

import life.catalogue.api.vocab.Users;

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