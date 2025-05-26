package life.catalogue.dao;

import life.catalogue.TestUtils;
import life.catalogue.api.exception.NotUniqueException;
import life.catalogue.api.model.CitationTest;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.concurrent.JobConfig;
import life.catalogue.config.GbifConfig;
import life.catalogue.config.ImporterConfig;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.db.mapper.DatasetMapperTest;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.event.BrokerConfig;
import life.catalogue.event.EventBroker;
import life.catalogue.img.ImageService;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.metadata.coldp.ColdpMetadataParser;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class DatasetDaoTest extends DaoTestBase {

  final ImporterConfig iCfg = new ImporterConfig();
  DatasetDao dao;

  @Before
  public void init() {
    DatasetImportDao diDao = new DatasetImportDao(SqlSessionFactoryRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    JobConfig cfg = new JobConfig();
    DatasetExportDao exDao = new DatasetExportDao(cfg, SqlSessionFactoryRule.getSqlSessionFactory(), validator);
    dao = new DatasetDao(factory(),
      new NormalizerConfig(), new ReleaseConfig(), iCfg, new GbifConfig(),
      null,
      ImageService.passThru(),
      diDao, exDao,
      NameUsageIndexService.passThru(),
      null,
      TestUtils.mockedBroker(),
      validator
    );
  }

  @Test
  public void doiDupeOverload() throws Exception {
    DOI doi = DOI.col("10.15468/dl.v0i1f3");
    Dataset d1 = DatasetMapperTest.create();
    d1.setDoi(doi);

    dao.create(d1, Users.TESTER);
    commit();

   for (int i = 0; i < 200; i++) {
     Dataset d2 = DatasetMapperTest.create();
     d2.setDoi(doi);
     try {
       dao.create(d2, Users.GBIF_SYNC);
     } catch (NotUniqueException e) {
       d2.setDoi(null);
       dao.create(d2, Users.GBIF_SYNC);

     }
   }

   System.out.println("done");
  }

  @Test
  public void brachyura() throws Exception {
    var d = ColdpMetadataParser.readYAML(getClass().getResourceAsStream("/brachyura.yml")).get().getDataset();
    d.setOrigin(DatasetOrigin.EXTERNAL);
    assertEquals(1, d.getEditor().size());
    dao.create(d, Users.TESTER);
    // the empty agent is removed from the list
    assertEquals(0, d.getEditor().size());
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
  public void publisherAlias() throws Exception {
    final UUID publisher = UUID.randomUUID();
    iCfg.publisherAlias.put(publisher, "plazi");
    Dataset d = DatasetMapperTest.create();
    d.setGbifPublisherKey(publisher);
    dao.create(d, Users.TESTER);
    commit();
    assertEquals("plazi1000", d.getAlias());

    // keep existing alias
    d = DatasetMapperTest.create();
    d.setGbifPublisherKey(publisher);
    d.setAlias("myPersonalAli");
    dao.create(d, Users.TESTER);
    commit();
    assertEquals("myPersonalAli", d.getAlias());

    // keep null if unkown publisher
    d = DatasetMapperTest.create();
    d.setGbifPublisherKey(UUID.randomUUID());
    d.setAlias(null);
    dao.create(d, Users.TESTER);
    commit();
    assertNull(d.getAlias());
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

  @Test(expected = NotUniqueException.class)
  public void duplicateDOI() throws Exception {
    final DOI doi = DOI.col("1234567");
    Dataset d = DatasetMapperTest.create();
    d.setDoi(doi);
    dao.create(d, Users.TESTER);

    d = DatasetMapperTest.create();
    d.setDoi(doi);
    dao.create(d, Users.TESTER);
  }

  @Test(expected = NotUniqueException.class)
  public void duplicateDOIupd() throws Exception {
    final DOI doi = DOI.col("1234567");
    Dataset d2 = DatasetMapperTest.create();
    d2.setDoi(doi);
    dao.create(d2, Users.TESTER);

    Dataset d = DatasetMapperTest.create();
    dao.create(d, Users.TESTER);

    d.setDoi(doi);
    dao.update(d, Users.TESTER);
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
