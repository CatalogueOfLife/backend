package life.catalogue.dao;

import life.catalogue.api.model.Page;
import life.catalogue.junit.SqlSessionFactoryRule;

import org.apache.ibatis.session.SqlSession;
import org.junit.Test;

public class ReferenceDaoTest extends DaoTestBase {
  
  /*
   * Issue #54 /reference (without query params) generates NPE.
   */
  @Test
  public void list() {
    try (SqlSession session = session()) {
      ReferenceDao dao = new ReferenceDao(SqlSessionFactoryRule.getSqlSessionFactory(), null, validator);
      dao.list(11, new Page());
    }
  }
  
}
