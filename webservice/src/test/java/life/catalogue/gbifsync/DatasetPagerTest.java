package life.catalogue.gbifsync;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.config.GbifConfig;

import java.time.LocalDate;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.logging.LoggingFeature;
import org.junit.Ignore;
import org.junit.Test;

import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

import static org.junit.Assert.assertEquals;


/**
 *
 */
public class DatasetPagerTest {

  @Test
  public void extractDomain() throws Exception {
    assertEquals("gbif", DatasetPager.extractDomain("http://gbif.org/dataset/23456"));
    assertEquals("gbif", DatasetPager.extractDomain("http://www.gbif.org/dataset/23456"));
    assertEquals("gbif", DatasetPager.extractDomain("https://www.gbif.org/dataset/23456"));
    assertEquals("gbif", DatasetPager.extractDomain("ftp://www.gbif.org/dataset/23456"));
    assertEquals("gbif", DatasetPager.extractDomain("https://api.dev.gbif.org/dataset/23456"));

    assertEquals("URL", DatasetPager.extractDomain("gbif-org-dataset/23456"));
    assertEquals("URL", DatasetPager.extractDomain("23456"));
  }

  @Test
  @Ignore("GBIF service needs to be mocked - this uses live services")
  public void datasetPager() throws Exception {
    final JacksonJsonProvider jacksonJsonProvider = new JacksonJaxbJsonProvider(ApiModule.MAPPER, JacksonJaxbJsonProvider.DEFAULT_ANNOTATIONS);
    ClientConfig cfg = new ClientConfig(jacksonJsonProvider);
    cfg.register(new LoggingFeature(Logger.getLogger(getClass().getName()), Level.ALL, LoggingFeature.Verbosity.PAYLOAD_ANY, 1024));
  
    final Client client = ClientBuilder.newClient(cfg);
    
    DatasetPager pager = new DatasetPager(client, new GbifConfig(), LocalDate.of(2023, 5, 23));

    // test VASCAN
    var vascan = pager.get(UUID.fromString("3f8a1297-3259-4700-91fc-acc4170b27ce"));
    System.out.println(vascan);

    while (pager.hasNext()) {
      pager.next().forEach(System.out::println);
    }
  }
  
}