package org.col.db.dao;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Page;
import org.junit.Test;

public class ReferenceDaoTest extends DaoTestBase {
  
  /*
   * Issue #54 /reference (without query params) generates NPE.
   */
  @Test
  public void list() {
    try (SqlSession session = session()) {
      ReferenceDao dao = new ReferenceDao(session);
      dao.list(11, new Page());
    }
  }
  
}
