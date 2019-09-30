package org.col.dao;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Page;
import org.col.db.PgSetupRule;
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
