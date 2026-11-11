package life.catalogue.gbifsync;

import life.catalogue.TestUtils;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.OutboxEntry;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.License;
import life.catalogue.api.vocab.OutboxType;
import life.catalogue.api.vocab.Setting;
import life.catalogue.api.vocab.Users;
import life.catalogue.config.GbifConfig;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.GbifRegistrationMapper;
import life.catalogue.db.mapper.MapperTestBase;
import life.catalogue.db.mapper.OutboxMapper;
import life.catalogue.doi.service.DoiConfig;
import life.catalogue.es.indexing.NameUsageIndexService;
import life.catalogue.img.ImageService;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.junit.TreeRepoRule;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import jakarta.validation.Validation;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

import static org.junit.Assert.*;

/**
 * Registers datasets in a fake GBIF registry and runs the GBIF_REGISTRY outbox commands against it.
 */
public class GbifRegistrarTest {
  // the external dataset of the apple test data
  static final int KEY = 12;
  static final int USER = Users.TESTER;
  static final URI API = URI.create("https://api.checklistbank.org");

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.apple();

  @Rule
  public TreeRepoRule treeRepoRule = new TreeRepoRule();

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  FakeGbifRegistry gbif;
  Client client;
  NormalizerConfig nCfg;
  DatasetDao dao;
  GbifRegistrar registrar;
  GbifRegistryHandler handler;
  final UUID org = UUID.randomUUID();

  @Before
  public void init() throws Exception {
    gbif = new FakeGbifRegistry();
    client = ClientBuilder.newClient();
    GbifConfig cfg = gbif.config();
    nCfg = new NormalizerConfig();
    nCfg.archiveDir = tmp.newFolder("archives");
    var factory = SqlSessionFactoryRule.getSqlSessionFactory();
    var validator = Validation.buildDefaultValidatorFactory().getValidator();
    var diDao = new DatasetImportDao(factory, TreeRepoRule.getRepo());
    dao = new DatasetDao(factory, nCfg, new ReleaseConfig(), cfg, new DoiConfig(), null, ImageService.passThru(), diDao,
      null, NameUsageIndexService.passThru(), null, TestUtils.mockedBroker(), validator);
    registrar = new GbifRegistrar(cfg, API, nCfg, factory, dao, new GbifRegistryClient(client, cfg));
    handler = new GbifRegistryHandler(registrar);

    // a public, openly licensed ColDP dataset imported once
    Dataset d = dao.get(KEY);
    d.setPrivat(false);
    d.setLicense(License.CC_BY);
    dao.update(d, USER);
    imported(DataFormat.COLDP, diDao);
  }

  @After
  public void close() {
    client.close();
    gbif.close();
  }

  private void imported(DataFormat format, DatasetImportDao diDao) throws Exception {
    var settings = new DatasetSettings();
    settings.put(Setting.DATA_FORMAT, format);
    dao.putSettings(KEY, settings, USER);
    var di = MapperTestBase.createSuccess(KEY, USER, diDao);
    File archive = nCfg.archive(KEY, di.getAttempt());
    archive.getParentFile().mkdirs();
    try (var zip = new ZipOutputStream(new FileOutputStream(archive))) {
      zip.putNextEntry(new ZipEntry("NameUsage.tsv"));
      zip.write("ID\tscientificName\n1\tAbies alba\n".getBytes());
      zip.closeEntry();
    }
  }

  @Test
  public void registerAndReconcile() throws Exception {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true);
         var st = session.getConnection().createStatement()) {
      st.execute("UPDATE dataset SET doi = '10.80631/ds12' WHERE key = " + KEY);
    }
    var reg = registrar.register(KEY, org, USER);
    UUID gbifKey = reg.getGbifKey();
    var g = gbif.datasets.get(gbifKey);
    assertEquals("CHECKLIST", g.path("type").asText());
    assertEquals(org.toString(), g.path("publishingOrganizationKey").asText());
    assertEquals(gbif.config().installationKey.toString(), g.path("installationKey").asText());
    assertEquals(License.CC_BY.getUrl(), g.path("license").asText());
    assertEquals("10.80631/ds12", g.path("doi").asText());
    assertEquals(gbifKey, dao.get(KEY).getGbifKey());
    assertEquals(org, dao.get(KEY).getGbifPublisherKey());

    // the outbox command adds endpoints and identifier and asks for a crawl
    runCommand();
    assertEquals("COLDP=https://api.checklistbank.org/dataset/12/archive.zip,EML=https://api.checklistbank.org/dataset/12.xml",
      endpoints(gbifKey));
    assertEquals(1, gbif.identifiers(gbifKey).size());
    assertEquals("12", gbif.identifiers(gbifKey).get(0).path("identifier").asText());
    assertEquals(1, gbif.crawls(gbifKey));

    // running it again changes nothing but crawls again
    registrar.reconcile(KEY, Set.of());
    assertEquals(2, gbif.endpoints(gbifKey).size());
    assertEquals(1, gbif.identifiers(gbifKey).size());
    assertEquals(2, gbif.crawls(gbifKey));

    // a new import in another format replaces the data endpoint
    imported(DataFormat.TEXT_TREE, new DatasetImportDao(SqlSessionFactoryRule.getSqlSessionFactory(), TreeRepoRule.getRepo()));
    registrar.reconcile(KEY, Set.of());
    assertEquals("EML=https://api.checklistbank.org/dataset/12.xml,TEXT_TREE=https://api.checklistbank.org/dataset/12/archive.zip",
      endpoints(gbifKey));

    // another organisation
    UUID org2 = UUID.randomUUID();
    registrar.changePublisher(KEY, org2, USER);
    runCommand();
    assertEquals(org2.toString(), gbif.datasets.get(gbifKey).path("publishingOrganizationKey").asText());

    // registering twice fails
    assertThrows(IllegalArgumentException.class, () -> registrar.register(KEY, org, USER));

    // deregister
    registrar.deregister(KEY, USER);
    assertNull(dao.get(KEY).getGbifKey());
    assertNull(registrar.status(KEY));
    runCommand();
    assertTrue(gbif.deleted.contains(gbifKey));
    // a second run finds nothing to delete anymore
    registrar.reconcile(KEY, Set.of(gbifKey));
  }

  @Test
  public void deleteDataset() throws Exception {
    UUID gbifKey = registrar.register(KEY, org, USER).getGbifKey();
    runCommand();
    dao.delete(KEY, USER);
    runCommand();
    assertTrue(gbif.deleted.contains(gbifKey));
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      assertFalse(session.getMapper(GbifRegistrationMapper.class).exists(KEY));
    }
  }

  @Test
  public void notRegistrable() throws Exception {
    // private
    Dataset d = dao.get(KEY);
    d.setPrivat(true);
    dao.update(d, USER);
    assertThrows(IllegalArgumentException.class, () -> registrar.register(KEY, org, USER));
    // closed license
    d = dao.get(KEY);
    d.setPrivat(false);
    d.setLicense(License.OTHER);
    dao.update(d, USER);
    assertThrows(IllegalArgumentException.class, () -> registrar.register(KEY, org, USER));
    // a project
    assertThrows(IllegalArgumentException.class, () -> registrar.register(TestDataRule.APPLE.key, org, USER));
    // nothing reached GBIF
    assertTrue(gbif.datasets.isEmpty());
  }

  @Test
  public void notZipped() throws Exception {
    var attempt = dao.get(KEY).getAttempt();
    File archive = nCfg.archive(KEY, attempt);
    java.nio.file.Files.writeString(archive.toPath(), "not a zip");
    assertThrows(IllegalArgumentException.class, () -> registrar.register(KEY, org, USER));
    assertTrue(gbif.datasets.isEmpty());
  }

  /**
   * If ChecklistBank fails to record a registration once the GBIF dataset exists, it must not leave an orphan in GBIF.
   */
  @Test
  public void compensate() throws Exception {
    // another dataset holds the GBIF key GBIF hands out next, so recording the registration violates the unique key
    UUID gbifKey = UUID.randomUUID();
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      session.getMapper(DatasetMapper.class).updateGbif(TestDataRule.APPLE.key, gbifKey, org, USER);
    }
    gbif.nextKey = gbifKey;
    assertThrows(RuntimeException.class, () -> registrar.register(KEY, org, USER));
    assertTrue(gbif.deleted.contains(gbifKey));
    assertTrue(gbif.datasets.isEmpty());
    assertNull(registrar.status(KEY));
    assertNull(dao.get(KEY).getGbifKey());
  }

  @Test
  public void handler() throws Exception {
    assertTrue(handler.isOnline());
    assertTrue(handler.isPermanent(new IllegalArgumentException()));
    assertTrue(handler.isPermanent(new GbifRegistryClient.GbifRegistryException(401, "no")));
    assertFalse(handler.isPermanent(new GbifRegistryClient.GbifRegistryException(500, "oops")));

    GbifConfig off = gbif.config();
    off.installationKey = null;
    var disabled = new GbifRegistryHandler(new GbifRegistrar(off, API, nCfg, SqlSessionFactoryRule.getSqlSessionFactory(), dao,
      new GbifRegistryClient(client, off)));
    assertFalse(disabled.isOnline());
  }

  private String endpoints(UUID gbifKey) {
    return gbif.endpoints(gbifKey).stream()
      .map(e -> e.path("type").asText() + "=" + e.path("url").asText())
      .sorted()
      .collect(Collectors.joining(","));
  }

  /**
   * Runs the pending GBIF_REGISTRY command of the dataset like the outbox runner would and removes it.
   */
  private void runCommand() throws Exception {
    OutboxEntry cmd = command();
    assertNotNull("No GBIF_REGISTRY command for dataset " + KEY, cmd);
    handler.handle(cmd);
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      session.getMapper(OutboxMapper.class).delete(cmd.getId());
    }
  }

  @Nullable
  private OutboxEntry command() {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      return session.getMapper(OutboxMapper.class).getByKey(OutboxType.GBIF_REGISTRY, String.valueOf(KEY));
    }
  }
}
