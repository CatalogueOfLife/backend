package life.catalogue.gbifsync;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import life.catalogue.config.GbifConfig;
import life.catalogue.gbifsync.GbifSync;
import life.catalogue.db.PgSetupRule;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.logging.LoggingFeature;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Long running tests to be manually executed when working on GbifSync")
public class GbifSyncTest {
  
  @ClassRule
  public static PgSetupRule pg = new PgSetupRule();
  
  @Test
  public void syncNow() {
    GbifConfig cfg = new GbifConfig();
    cfg.syncFrequency = 1;
    cfg.insert = true;
  
    final JacksonJsonProvider jacksonJsonProvider = new JacksonJaxbJsonProvider()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    ClientConfig ccfg = new ClientConfig(jacksonJsonProvider);
    ccfg.register(new LoggingFeature(Logger.getLogger(getClass().getName()), Level.ALL, LoggingFeature.Verbosity.PAYLOAD_ANY, 1024));
    final Client client = ClientBuilder.newClient(ccfg);
    
    try {
      GbifSync gbif = new GbifSync(cfg, PgSetupRule.getSqlSessionFactory(), client);
      gbif.syncNow();
      
    } finally {
      client.close();
    }
    
  }
}