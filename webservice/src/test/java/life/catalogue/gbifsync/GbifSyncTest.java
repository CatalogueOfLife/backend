package life.catalogue.gbifsync;

import life.catalogue.config.GbifConfig;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.db.PgSetupRule;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.validation.Validator;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.logging.LoggingFeature;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@Ignore("Long running tests to be manually executed when working on GbifSync")
@RunWith(MockitoJUnitRunner.class)
public class GbifSyncTest {
  
  @ClassRule
  public static PgSetupRule pg = new PgSetupRule();

  @Mock
  Validator validator;
  @Mock
  DatasetImportDao diDao;

  @Test
  public void syncNow() {
    GbifConfig cfg = new GbifConfig();
    cfg.syncFrequency = 1;

    final JacksonJsonProvider jacksonJsonProvider = new JacksonJaxbJsonProvider()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    ClientConfig ccfg = new ClientConfig(jacksonJsonProvider);
    ccfg.register(new LoggingFeature(Logger.getLogger(getClass().getName()), Level.ALL, LoggingFeature.Verbosity.PAYLOAD_ANY, 1024));
    final Client client = ClientBuilder.newClient(ccfg);
    var ddao = new DatasetDao(PgSetupRule.getSqlSessionFactory(), null, diDao, validator);

    try {
      GbifSyncManager gbif = new GbifSyncManager(cfg, ddao, PgSetupRule.getSqlSessionFactory(), client);
      gbif.syncNow();
      
    } finally {
      client.close();
    }
    
  }
}