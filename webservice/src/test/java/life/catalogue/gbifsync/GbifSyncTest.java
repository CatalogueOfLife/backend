package life.catalogue.gbifsync;

import life.catalogue.api.vocab.JobStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.config.GbifConfig;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;

import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.validation.Validation;
import javax.validation.Validator;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.logging.LoggingFeature;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

@Ignore("Long running tests to be manually executed when working on GbifSync")
@RunWith(MockitoJUnitRunner.class)
public class GbifSyncTest {
  
  @ClassRule
  public static final PgSetupRule pg = new PgSetupRule();
  private static final GbifConfig cfg = new GbifConfig();
  private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
  @Mock
  DatasetImportDao diDao;
  private static Client client;
  private static DatasetDao ddao;

  @BeforeClass
  public static void init() {
    cfg.syncFrequency = 1;
    final JacksonJsonProvider jacksonJsonProvider = new JacksonJaxbJsonProvider()
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
    ddao = new DatasetDao(SqlSessionFactoryRule.getSqlSessionFactory(), null, diDao, validator);
  }

  @Test
  public void syncNow() {
    GbifSyncManager gbif = new GbifSyncManager(cfg, ddao, SqlSessionFactoryRule.getSqlSessionFactory(), client);
    gbif.syncNow();
  }

  @Test
  public void syncSingle() {
    GbifSyncJob job = new GbifSyncJob(cfg, client, ddao, SqlSessionFactoryRule.getSqlSessionFactory(), Users.GBIF_SYNC, Set.of(UUID.fromString("30f55c63-a829-4cb2-9676-3b1b6f981567")), false);
    job.run();
    Assert.assertEquals(JobStatus.FINISHED, job.getStatus());
  }
}