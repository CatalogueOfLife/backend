package org.col.dao;

import org.col.api.TestEntityGenerator;
import org.col.api.model.Name;
import org.col.api.vocab.Users;
import org.col.common.tax.AuthorshipNormalizer;
import org.col.db.PgSetupRule;
import org.junit.Assert;
import org.junit.Test;

public class NameDaoTest extends DaoTestBase {
  static final AuthorshipNormalizer aNormalizer = AuthorshipNormalizer.createWithAuthormap();
  
  NameDao dao = new NameDao(PgSetupRule.getSqlSessionFactory(), aNormalizer);
  
  @Test
  public void authorshipNormalization() throws Exception {
    Name n1 = TestEntityGenerator.newName("n2");

    dao.create(n1, Users.IMPORTER);
    Assert.assertNotNull(n1.getAuthorshipNormalized());
  }
}
