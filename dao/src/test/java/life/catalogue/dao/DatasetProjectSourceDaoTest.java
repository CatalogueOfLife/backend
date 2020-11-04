package life.catalogue.dao;

import life.catalogue.db.TestDataRule;
import org.junit.Test;

import static org.junit.Assert.*;

public class DatasetProjectSourceDaoTest extends DaoTestBase {

  public DatasetProjectSourceDaoTest(){
    super(TestDataRule.fish());
  }

  @Test
  public void list() {
    DatasetProjectSourceDao dao = new DatasetProjectSourceDao(factory());
    dao.list(TestDataRule.FISH.key, null).forEach(d -> {
      System.out.println(d.getCitation());
    });
  }
}