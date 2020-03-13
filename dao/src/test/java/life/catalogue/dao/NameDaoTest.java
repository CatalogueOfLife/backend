package life.catalogue.dao;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Name;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.db.PgSetupRule;
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
