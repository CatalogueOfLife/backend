package life.catalogue.dao;

import life.catalogue.TestUtils;
import life.catalogue.api.event.Event;
import life.catalogue.api.event.UserPermissionChanged;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Setting;
import life.catalogue.api.vocab.Users;
import life.catalogue.event.EventBroker;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.concurrent.JobConfig;
import life.catalogue.config.GbifConfig;
import life.catalogue.config.ImporterConfig;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.db.mapper.DatasetMapperTest;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.db.mapper.SectorProfileMapper;
import life.catalogue.doi.service.DoiConfig;
import life.catalogue.es.indexing.NameUsageIndexService;
import life.catalogue.img.ImageService;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.metadata.coldp.ColdpMetadataParser;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DatasetDaoTest extends DaoTestBase {

  final ImporterConfig iCfg = new ImporterConfig();
  DatasetDao dao;
  EventBroker broker;

  @Before
  public void init() {
    DatasetImportDao diDao = new DatasetImportDao(SqlSessionFactoryRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    JobConfig cfg = new JobConfig();
    DatasetExportDao exDao = new DatasetExportDao(cfg, SqlSessionFactoryRule.getSqlSessionFactory(), validator);
    broker = TestUtils.mockedBroker();
    dao = new DatasetDao(factory(),
      new NormalizerConfig(), new ReleaseConfig(), new GbifConfig(), new DoiConfig(),
      null,
      ImageService.passThru(),
      diDao, exDao,
      NameUsageIndexService.passThru(),
      null,
      broker,
      validator
    );
  }

  @Test
  public void doiDupeOverload() throws Exception {
    DOI doi = DOI.col("10.15468/dl.v0i1f3");
    Dataset d1 = DatasetMapperTest.create();
    d1.addIdentifier(doi);

    dao.create(d1, Users.TESTER);
    commit();

   for (int i = 0; i < 200; i++) {
     Dataset d2 = DatasetMapperTest.create();
     d2.addIdentifier(doi);
     dao.create(d2, Users.GBIF_SYNC);
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
  public void deleteTempDatasets() throws Exception {
    assertEquals(0, dao.deleteTempDatasets(null));
  }

  /**
   * A temp dataset publishes no DatasetChanged event, so AuthBundle never patches the creator's cached User.editor set.
   * createTemp must therefore publish a UserPermissionChanged for the human creator so their auth cache is invalidated
   * and the acl_editor grant (added by createWithID) is picked up on the next request rather than after the password
   * cache expiry. See https://github.com/CatalogueOfLife/backend private-temp-dataset 403 bug.
   */
  @Test
  public void createTempInvalidatesCreatorAuthCache() throws Exception {
    final int userKey = 123; // a real human user (>= Users.MIN_HUMAN_KEY, so !isBot) - only they get an acl_editor grant
    final String username = "tempcreator";
    try (SqlSession s = factory().openSession(true)) {
      s.getConnection().createStatement().execute(
        "INSERT INTO \"user\" (key, username) VALUES (" + userKey + ", '" + username + "')");
    }

    Dataset d = new Dataset();
    d.setTitle("temp validation");
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setType(DatasetType.OTHER);
    d.setPrivat(true);
    dao.createTemp(d, userKey);

    ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
    verify(broker, atLeastOnce()).publish(captor.capture());
    assertTrue("expected a UserPermissionChanged for the creator",
      captor.getAllValues().stream()
        .anyMatch(e -> e instanceof UserPermissionChanged && username.equals(((UserPermissionChanged) e).username)));
  }

  /**
   * System/bot users (key < 100) never receive an acl_editor grant, so no auth cache invalidation is needed.
   */
  @Test
  public void createTempSkipsAuthInvalidationForBots() throws Exception {
    Dataset d = new Dataset();
    d.setTitle("temp validation by bot");
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setType(DatasetType.OTHER);
    d.setPrivat(true);
    dao.createTemp(d, Users.IMPORTER); // key 10 < 100

    verify(broker, never()).publish(any(UserPermissionChanged.class));
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
  public void articleAlias() throws Exception {
    final UUID publisher = UUID.randomUUID();
    Dataset d = DatasetMapperTest.create();
    d.setAlias(null);
    d.setGbifPublisherKey(publisher);
    d.setType(DatasetType.ARTICLE);
    var cit = Citation.create("Aha");
    cit.setAuthor(List.of(new CslName("Paul", "Möglich", "von")));
    cit.setIssued(FuzzyDate.of(2017));
    d.setSource(List.of(cit));

    dao.create(d, Users.TESTER);
    commit();
    assertEquals("vonMöglich2017", d.getAlias());

    // keep existing alias
    d = DatasetMapperTest.create();
    d.setGbifPublisherKey(publisher);
    d.setAlias("myPersonalAli");
    d.setType(DatasetType.ARTICLE);
    dao.create(d, Users.TESTER);
    commit();
    assertEquals("myPersonalAli", d.getAlias());

    // use dataset creator otherwise if no source
    d = DatasetMapperTest.create();
    d.setAlias(null);
    d.setType(DatasetType.ARTICLE);
    d.setCreator(List.of(Agent.person("Paul", "Mägdefrau")));
    d.setIssued(FuzzyDate.of(2018, 12, 6));
    dao.create(d, Users.TESTER);
    commit();
    assertEquals("Mägdefrau2018", d.getAlias());

    // keep null if no source nor author
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

  /**
   * The max container authors follow the number of project creators, unless they were set manually to another value.
   */
  @Test
  public void maxContainerAuthorsFollowCreators() throws Exception {
    Dataset proj = DatasetMapperTest.create();
    proj.setOrigin(DatasetOrigin.PROJECT);
    proj.setCreator(List.of(Agent.person("Anna", "Ast"), Agent.person("Bert", "Baum")));
    dao.create(proj, Users.TESTER);
    assertNull(dao.getSettings(proj.getKey()).getInt(Setting.SOURCE_MAX_CONTAINER_AUTHORS));

    updateCreators(proj.getKey(), 3);
    assertEquals((Integer) 3, dao.getSettings(proj.getKey()).getInt(Setting.SOURCE_MAX_CONTAINER_AUTHORS));
    updateCreators(proj.getKey(), 4);
    assertEquals((Integer) 4, dao.getSettings(proj.getKey()).getInt(Setting.SOURCE_MAX_CONTAINER_AUTHORS));

    // a manual value sticks
    var ds = dao.getSettings(proj.getKey());
    ds.put(Setting.SOURCE_MAX_CONTAINER_AUTHORS, 2);
    dao.putSettings(proj.getKey(), ds, Users.TESTER);
    updateCreators(proj.getKey(), 5);
    assertEquals((Integer) 2, dao.getSettings(proj.getKey()).getInt(Setting.SOURCE_MAX_CONTAINER_AUTHORS));
  }

  private void updateCreators(int key, int size) {
    var d = dao.get(key);
    List<Agent> creators = new java.util.ArrayList<>();
    for (int i = 0; i < size; i++) {
      creators.add(Agent.person("Given" + i, "Family" + i));
    }
    d.setCreator(creators);
    dao.update(d, Users.TESTER);
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

  /**
   * Profiles go with the sectors: removed with a project or a private release, kept for a deleted public release.
   */
  @Test
  public void deleteRemovesProfiles() {
    Dataset proj = DatasetMapperTest.create();
    proj.setOrigin(DatasetOrigin.PROJECT);
    dao.create(proj, Users.TESTER);
    int privRelease = createRelease(proj.getKey(), true);
    int pubRelease = createRelease(proj.getKey(), false);
    for (int key : List.of(proj.getKey(), privRelease, pubRelease)) {
      createProfile(key);
    }

    dao.delete(privRelease, Users.TESTER);
    assertEquals(0, countProfiles(privRelease));

    dao.delete(pubRelease, Users.TESTER);
    assertEquals(1, countProfiles(pubRelease));

    // a project takes all its releases with it, public ones entirely, exactly like their sectors
    dao.delete(proj.getKey(), Users.TESTER);
    assertEquals(0, countProfiles(proj.getKey()));
    assertEquals(0, countProfiles(pubRelease));
  }

  /**
   * deleteData skips the foreign key checks for the whole dataset delete
   * and leaves the pooled connection with its triggers enabled again.
   */
  @Test
  public void deleteDataSkipsChecksAndRestoresRole() throws Exception {
    final int key = TestDataRule.APPLE.key;
    try (SqlSession session = factory().openSession(true)) {
      assertTrue(count(session, "name_usage", key) > 0);
      assertTrue(session.getMapper(DatasetPartitionMapper.class).canSkipTriggers());

      dao.deleteData(key, session);

      for (String t : DatasetPartitionMapper.PARTITIONED_TABLES) {
        assertEquals(t, 0, count(session, t, key));
      }
      assertEquals("origin", replicationRole(session));
    }
  }

  private static int count(SqlSession session, String table, int datasetKey) throws Exception {
    try (var st = session.getConnection().createStatement();
         var rs = st.executeQuery("SELECT count(*) FROM " + table + " WHERE dataset_key=" + datasetKey)) {
      rs.next();
      return rs.getInt(1);
    }
  }

  private static String replicationRole(SqlSession session) throws Exception {
    try (var st = session.getConnection().createStatement();
         var rs = st.executeQuery("SHOW session_replication_role")) {
      rs.next();
      return rs.getString(1);
    }
  }

  private void createProfile(int datasetKey) {
    try (SqlSession session = factory().openSession(true)) {
      var p = new SectorProfile();
      p.setDatasetKey(datasetKey);
      p.setTitle("profile of " + datasetKey);
      p.applyUser(Users.TESTER);
      session.getMapper(SectorProfileMapper.class).create(p);
    }
  }

  private int countProfiles(int datasetKey) {
    try (SqlSession session = factory().openSession(true)) {
      return session.getMapper(SectorProfileMapper.class).count(datasetKey);
    }
  }

  int createRelease(int projectKey, boolean privat) {
    Dataset d = DatasetMapperTest.create();
    d.setSourceKey(projectKey);
    d.setOrigin(DatasetOrigin.RELEASE);
    d.setPrivat(privat);
    dao.create(d, Users.TESTER);
    return d.getKey();
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
