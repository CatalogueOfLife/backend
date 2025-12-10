package life.catalogue.gbifsync;

import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;

import jakarta.ws.rs.client.ClientBuilder;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.vocab.Country;
import life.catalogue.api.vocab.Publishers;
import life.catalogue.config.GbifConfig;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.logging.LoggingFeature;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PublisherSyncJobTest {

  @Test
  @Disabled("GBIF service needs to be mocked - this uses live services")
  void getFromGBIF() throws Exception {
    var gcfg = new GbifConfig();

    final JacksonJsonProvider jacksonJsonProvider = new JacksonJsonProvider(ApiModule.MAPPER);
    ClientConfig cfg = new ClientConfig();
    cfg.register(jacksonJsonProvider);
    cfg.register(new LoggingFeature(Logger.getLogger(getClass().getName()), Level.ALL, LoggingFeature.Verbosity.PAYLOAD_ANY, 1024));

    var cl = ClientBuilder.newClient(cfg);
    var job = new PublisherSyncJob(gcfg, cl, null, 1);
    var plazi = job.getFromGBIF(Publishers.PLAZI);
    assertNotNull(plazi);
    assertEquals(Publishers.PLAZI, plazi.getKey());
    assertEquals("Plazi.org taxonomic treatments database", plazi.getTitle());
    assertEquals(Country.SWITZERLAND.getName(), plazi.getCountry());
  }
}