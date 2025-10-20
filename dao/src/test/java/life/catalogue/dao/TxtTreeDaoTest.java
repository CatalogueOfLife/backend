package life.catalogue.dao;

import life.catalogue.es.NameUsageIndexService;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.junit.Before;
import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.NAME4;
import static life.catalogue.api.TestEntityGenerator.TAXON2;

public class TxtTreeDaoTest extends DaoTestBase {
  TxtTreeDao dao;

  public TxtTreeDaoTest() {
    super(TestDataRule.tree());
  }

  @Before
  public void init() throws IOException {
    dao = new TxtTreeDao(SqlSessionFactoryRule.getSqlSessionFactory(), null, null, NameUsageIndexService.passThru(), null);
  }

  @Test
  public void inclParents() throws IOException {
    Writer w = new StringWriter();
    dao.readTxtree(TestDataRule.TREE.key, "t30", false, true, null, w);
    System.out.println(w.toString());
  }

}