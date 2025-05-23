package life.catalogue.gbifsync;

import life.catalogue.TestUtils;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.config.GbifConfig;
import life.catalogue.config.ImporterConfig;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;

import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.logging.LoggingFeature;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

@Ignore("Long running tests to be manually executed when working on GbifSync")
@RunWith(MockitoJUnitRunner.class)
public class GbifSyncTest {
  
  @ClassRule
  public static final PgSetupRule pg = new PgSetupRule();
  private static final GbifConfig cfg = new GbifConfig();
  private static final ImporterConfig iCfg = new ImporterConfig();
  private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
  @Mock
  DatasetImportDao diDao;
  private static Client client;
  private static DatasetDao ddao;

  @BeforeClass
  public static void init() {
    cfg.syncFrequency = 1;
    final JacksonJsonProvider jacksonJsonProvider = new JacksonJsonProvider()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    ClientConfig ccfg = new ClientConfig(jacksonJsonProvider);
    ccfg.register(new LoggingFeature(Logger.getLogger(GbifSyncTest.class.getName()), Level.ALL, LoggingFeature.Verbosity.PAYLOAD_ANY, 1024));
    client = ClientBuilder.newClient(ccfg);
  }

  @AfterClass
  public static void destroy() {
    client.close();
  }

  @Before
  public void initTest() {
    ddao = new DatasetDao(SqlSessionFactoryRule.getSqlSessionFactory(), null, diDao, validator, TestUtils.mockedBroker());
  }

  @Test
  public void syncNow() {
    GbifSyncManager gbif = new GbifSyncManager(cfg, iCfg, ddao, SqlSessionFactoryRule.getSqlSessionFactory(), client);
    gbif.syncNow();
  }

  @Test
  public void syncSingle() {
    GbifSyncJob job = new GbifSyncJob(cfg, iCfg, client, ddao, SqlSessionFactoryRule.getSqlSessionFactory(), Users.GBIF_SYNC, Set.of(UUID.fromString("30f55c63-a829-4cb2-9676-3b1b6f981567")), false);
    job.run();
    Assert.assertEquals(JobStatus.FINISHED, job.getStatus());
  }
}