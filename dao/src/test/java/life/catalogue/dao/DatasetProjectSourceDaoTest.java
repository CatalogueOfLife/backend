package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.db.mapper.*;
import life.catalogue.junit.TestDataRule;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
      System.out.println(d.getKey() +": " + d.getTitle() + " " + d.isMerged());
      // the container without its creators, which would repeat on every source
      assertEquals((Integer) TestDataRule.FISH.key, d.getContainerKey());
      assertNull(d.getContainerCreator());
    });
  }

  @Test
  public void get() {
    DatasetSourceDao dao = new DatasetSourceDao(factory());
    var src = dao.get(103, 100, true);
    System.out.println(src);
  }

  @Test
  public void getSimple() {
    DatasetSourceDao dao = new DatasetSourceDao(factory());
    var src = dao.getSimple(3, 100);
    System.out.println(src);
    src = dao.getSimple(3, 101);
    System.out.println(src);
  }

  @Test
  public void projectSourceMetrics() {
    DatasetSourceDao dao = new DatasetSourceDao(factory());
    dao.sourceMetrics(3, 100, null);
    dao.sourceMetrics(3, 101, null);
    dao.sourceMetrics(3, 102, null);
  }

  /**
   * The UI asks for the non merge metrics of a source with merged=false, which must include hierarchy sectors.
   */
  @Test
  public void hierarchySourceMetrics() {
    final int projectKey = TestDataRule.FISH.key;
    var d = DatasetMapperTest.create();
    mapper(DatasetMapper.class).create(d);
    final int src = d.getKey();
    createSyncedSector(projectKey, src, Sector.Mode.HIERARCHY, 42);
    createSyncedSector(projectKey, src, Sector.Mode.ATTACH, 8);
    commit();

    DatasetSourceDao dao = new DatasetSourceDao(factory());
    var m = dao.sourceMetrics(projectKey, src, false);
    assertEquals(2, (int) m.getSectorCount());
    assertEquals(50, (int) m.getTaxonCount());

    assertEquals(0, (int) dao.sourceMetrics(projectKey, src, true).getSectorCount());

    m = dao.sourceMetrics(projectKey, src, null);
    assertEquals(2, (int) m.getSectorCount());
    assertEquals(50, (int) m.getTaxonCount());
  }

  private void createSyncedSector(int projectKey, int sourceKey, Sector.Mode mode, int taxonCount) {
    Sector s = SectorMapperTest.create(DSID.of(projectKey, "t" + mode), DSID.of(sourceKey, "s" + mode));
    s.setMode(mode);
    if (mode == Sector.Mode.HIERARCHY) {
      s.setSubject(null);
      s.setTarget(null);
    }
    mapper(SectorMapper.class).create(s);
    var si = SectorImportMapperTest.create(JobStatus.FINISHED, s);
    si.setTaxonCount(taxonCount);
    MapperTestBase.createJob(session(), si);
    mapper(SectorImportMapper.class).create(si);
    mapper(SectorMapper.class).updateLastSync(s, si.getAttempt());
  }

}
