package life.catalogue.dao;

import life.catalogue.api.model.CitationTest;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.mapper.DatasetMapperTest;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.img.ImageService;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.validation.ConstraintViolationException;

import org.junit.Before;
import org.junit.Test;

import com.google.common.eventbus.EventBus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DatasetDaoTest extends DaoTestBase {

  DatasetDao dao;

  @Before
  public void init() {
    DatasetImportDao diDao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    DatasetExportDao exDao = new DatasetExportDao(new File("/tmp/exports"), PgSetupRule.getSqlSessionFactory(), new EventBus(), validator);
    dao = new DatasetDao(3, factory(),
      null,
      ImageService.passThru(),
      diDao, exDao,
      NameUsageIndexService.passThru(),
      null,
      new EventBus(),
      validator
    );
  }

  @Test
  public void keygen() throws Exception {
    DatasetDao.KeyGenerator gen = new DatasetDao.KeyGenerator(10, 8, 10);
    assertEquals(9, gen.nextExternalKey());
    assertEquals(11, gen.nextExternalKey());
    assertEquals(12, gen.nextExternalKey());
    assertEquals(13, gen.nextExternalKey());
    assertEquals(14, gen.nextExternalKey());
    assertEquals(15, gen.nextExternalKey());
    assertEquals(16, gen.nextExternalKey());
    assertEquals(17, gen.nextExternalKey());
    assertEquals(18, gen.nextExternalKey());
    assertEquals(19, gen.nextExternalKey());
    assertEquals(21, gen.nextExternalKey());
    assertEquals(22, gen.nextExternalKey());

    assertEquals(20, gen.nextProjectKey());
    assertEquals(30, gen.nextProjectKey());
  }

  @Test
  public void roundtrip() throws Exception {
    Dataset d1 = DatasetMapperTest.create();
    d1.setSource(List.of(
      CitationTest.create(),
      CitationTest.create()
    ));

    dao.create(d1, Users.TESTER);
    commit();

    var d2 = dao.get(d1.getKey());
    //printDiff(u1, u2);
    assertEquals(d1, d2);
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalid() throws Exception {
    Dataset d = DatasetMapperTest.create();
    d.setOrigin(null);
    dao.create(d, Users.TESTER);
  }

  @Test(expected = IllegalArgumentException.class)
  public void deleteCOL() {
    dao.delete(Datasets.COL, Users.TESTER);
  }

  @Test
  public void deleteProject() {
    Dataset proj = DatasetMapperTest.create();
    proj.setOrigin(DatasetOrigin.MANAGED);
    dao.create(proj, Users.TESTER);

    Set<Integer> releaseKeys = new HashSet<>();
    releaseKeys.add(createRelease(proj.getKey()));
    releaseKeys.add(createRelease(proj.getKey()));
    releaseKeys.add(createRelease(proj.getKey()));

    dao.delete(proj.getKey(), Users.TESTER);

    assertDeleted(proj.getKey());
    for (int key : releaseKeys) {
      assertDeleted(key);
    }
  }

  void assertDeleted(int key){
    assertNotNull(dao.get(key).getDeleted());
  }

  int createRelease(int projectKey) {
    Dataset d = DatasetMapperTest.create();
    d.setSourceKey(projectKey);
    d.setOrigin(DatasetOrigin.RELEASED);
    dao.create(d, Users.TESTER);
    return d.getKey();
  }

}
