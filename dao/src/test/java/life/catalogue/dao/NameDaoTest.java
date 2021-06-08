package life.catalogue.dao;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.IndexName;
import life.catalogue.api.model.Name;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.NameIndexFactory;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NameDaoTest extends DaoTestBase {

  static final IndexName match = new IndexName(TestEntityGenerator.NAME4, 1);
  NameDao dao = new NameDao(PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), NameIndexFactory.fixed(match));
  
  @Test
  public void authorshipNormalization() throws Exception {
    Name n1 = TestEntityGenerator.newName("n2");

    dao.create(n1, Users.IMPORTER);
    Assert.assertNotNull(n1.getAuthorshipNormalized());
  }

  @Test
  public void nameMatching() throws Exception {
    Name n = TestEntityGenerator.newName("n2");
    dao.create(n, Users.IMPORTER);

    NameMapper.NameWithNidx nidx = mapper(NameMapper.class).getWithNidx(n);
    assertEquals(MatchType.VARIANT, nidx.namesIndexType);
    assertEquals(match.getKey(), nidx.namesIndexId);
  }
}
