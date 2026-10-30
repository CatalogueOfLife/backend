package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.VerbatimSource;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.InfoGroup;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.db.mapper.*;
import life.catalogue.junit.TestDataRule;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

  /**
   * The source table asks for merged and non merged metrics separately if a source has sectors of both kinds.
   * The contribution has no sector to tell them apart, so only one of them may report it.
   */
  @Test
  public void contributionCountedOnce() {
    int release;
    try (SqlSession session = factory().openSession(true)) {
      Dataset d = DatasetMapperTest.create();
      d.setOrigin(DatasetOrigin.RELEASE);
      d.setSourceKey(3);
      session.getMapper(DatasetMapper.class).create(d);
      release = d.getKey();
      session.getMapper(DatasetPartitionMapper.class).createSequences(release);

      var sm = session.getMapper(SectorMapper.class);
      for (Sector.Mode mode : List.of(Sector.Mode.ATTACH, Sector.Mode.MERGE)) {
        Sector s = SectorMapperTest.create(DSID.of(release, "t" + mode), DSID.of(100, "s" + mode));
        s.setMode(mode);
        sm.create(s);
      }
      Sector s = SectorMapperTest.create(DSID.of(release, "t"), DSID.of(101, "s"));
      s.setMode(Sector.Mode.ATTACH);
      sm.create(s);

      var vsm = session.getMapper(VerbatimSourceMapper.class);
      int id = 1;
      for (int source : new int[]{100, 101}) {
        var vs = new VerbatimSource(release, id++, null, null, null, null);
        vsm.create(vs);
        vsm.insertSources(vs, DSID.of(source, "x"), Set.of(InfoGroup.PUBLISHED_IN_PAGE_LINK));
      }
      session.getMapper(DatasetSourceContributionMapper.class).createForRelease(release);
    }
    DatasetInfoCache.CACHE.clear();

    DatasetSourceDao dao = new DatasetSourceDao(factory());
    var expected = Map.of(InfoGroup.PUBLISHED_IN_PAGE_LINK, 1);

    var all = dao.sourceMetrics(release, 100, null);
    assertEquals((Integer) 1, all.getEnrichedCount());
    assertEquals(expected, all.getEnrichedByInfoCount());
    var merged = dao.sourceMetrics(release, 100, true);
    assertEquals((Integer) 1, merged.getEnrichedCount());
    var other = dao.sourceMetrics(release, 100, false);
    assertNull(other.getEnrichedCount());
    assertTrue(other.getEnrichedByInfoCount().isEmpty());

    // without a merge sector it is the only row of that source
    assertEquals((Integer) 1, dao.sourceMetrics(release, 101, false).getEnrichedCount());

    // projects report no contribution and never read it
    assertNull(dao.sourceMetrics(3, 100, null).getEnrichedCount());
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
