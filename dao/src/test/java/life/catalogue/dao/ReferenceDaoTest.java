package life.catalogue.dao;

import org.apache.ibatis.session.SqlSession;
import life.catalogue.api.model.Page;
import life.catalogue.db.PgSetupRule;
import org.junit.Test;

public class ReferenceDaoTest extends DaoTestBase {
  
  /*
   * Issue #54 /reference (without query params) generates NPE.
   */
  @Test
  public void list() {
    try (SqlSession session = session()) {
      ReferenceDao dao = new ReferenceDao(PgSetupRule.getSqlSessionFactory());
      dao.list(11, new Page());
    }
  }
  
}
