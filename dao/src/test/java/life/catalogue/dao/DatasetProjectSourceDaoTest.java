package life.catalogue.dao;

import life.catalogue.api.vocab.Datasets;
import life.catalogue.junit.TestDataRule;

import org.junit.Test;

public class DatasetProjectSourceDaoTest extends DaoTestBase {

  public DatasetProjectSourceDaoTest(){
    super(TestDataRule.fish());
  }

  @Test
  public void list() {
    DatasetSourceDao dao = new DatasetSourceDao(factory());
    dao.listSectorBasedSources(TestDataRule.FISH.key, Datasets.COL, true).forEach(d -> {
      System.out.println(d.getTitle());
    });

    dao.listSimple(TestDataRule.FISH.key, true, true).forEach(d -> {
      System.out.println(d.getTitle() + " " + d.isMerged());
    });
  }

  @Test
  public void projectSourceMetrics() {
    DatasetSourceDao dao = new DatasetSourceDao(factory());
    dao.sourceMetrics(3, 100, null);
    dao.sourceMetrics(3, 101, null);
    dao.sourceMetrics(3, 102, null);
  }

}