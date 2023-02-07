package life.catalogue.dao;

import life.catalogue.api.exception.NotUniqueException;
import life.catalogue.api.model.CitationTest;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.mapper.DatasetMapperTest;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.img.ImageService;

import java.io.File;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    dao = new DatasetDao(testDataRule.keyGenerator.minExternalDatasetKey, factory(),
      new NormalizerConfig(), new ReleaseConfig(),
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
    DatasetDao.KeyGenerator gen = new DatasetDao.KeyGenerator(100, 108, 3);
    assertEquals(109, gen.nextExternalKey());
    assertEquals(110, gen.nextExternalKey());
    assertEquals(111, gen.nextExternalKey());
    assertEquals(112, gen.nextExternalKey());

    assertEquals(4, gen.nextProjectKey());
    assertEquals(5, gen.nextProjectKey());
  }

  @Test(expected = IllegalStateException.class)
  public void keygenThrow() throws Exception {
    DatasetDao.KeyGenerator gen = new DatasetDao.KeyGenerator(100, 108, 98);
    assertEquals(109, gen.nextExternalKey());
    assertEquals(110, gen.nextExternalKey());
    assertEquals(111, gen.nextExternalKey());

    assertEquals(99, gen.nextProjectKey());
    gen.nextProjectKey();
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

  @Test
  public void patch() throws Exception {
    Dataset d1 = DatasetMapperTest.create();
    d1.setSource(List.of(
      CitationTest.create(),
      CitationTest.create()
    ));

    dao.create(d1, Users.TESTER);
    commit();

    Dataset upd = new Dataset();
    upd.setLogo(URI.create("https://unite.ut.ee/img/unite-logo-web.svg"));

    DatasetWithSettings ds = new DatasetWithSettings(d1, dao.getSettings(d1.getKey()));
    dao.patchMetadata(ds, upd);
    dao.update(ds.getDataset(), Users.TESTER);

    assertEquals(upd.getLogo(), dao.get(d1.getKey()).getLogo());
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalid() throws Exception {
    Dataset d = DatasetMapperTest.create();
    d.setOrigin(null);
    dao.create(d, Users.TESTER);
  }

  @Test(expected = IllegalArgumentException.class)
  public void duplicateDOI() throws Exception {
    final DOI doi = DOI.col("1234567");
    Dataset d = DatasetMapperTest.create();
    d.setDoi(doi);
    dao.create(d, Users.TESTER);

    d = DatasetMapperTest.create();
    d.setDoi(doi);
    dao.create(d, Users.TESTER);
  }

  @Test(expected = IllegalArgumentException.class)
  public void deleteCOL() {
    dao.delete(Datasets.COL, Users.TESTER);
  }

  @Test
  public void deleteProject() {
    Dataset proj = DatasetMapperTest.create();
    proj.setOrigin(DatasetOrigin.PROJECT);
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
    d.setOrigin(DatasetOrigin.RELEASE);
    dao.create(d, Users.TESTER);
    return d.getKey();
  }

}
