package life.catalogue.doi.service;

import life.catalogue.api.jackson.ApiModule;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import life.catalogue.api.model.DOI;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.logging.LoggingFeature;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

@Ignore("Using real DataCite API - manual test only")
public class DataCiteServiceIT {

  DataCiteService service;

  @Before
  public void setup(){
    final JacksonJsonProvider jacksonJsonProvider = new JacksonJaxbJsonProvider(ApiModule.MAPPER, JacksonJaxbJsonProvider.DEFAULT_ANNOTATIONS);
    ClientConfig cfg = new ClientConfig(jacksonJsonProvider);
    cfg.register(new LoggingFeature(Logger.getLogger(getClass().getName()), Level.ALL, LoggingFeature.Verbosity.PAYLOAD_ANY, 1024));

    final Client client = ClientBuilder.newClient(cfg);

    DoiConfig doiCfg = new DoiConfig();
    doiCfg.api = "https://api.datacite.org";
    service = new DataCiteService(doiCfg, client);
  }

  @Test
  public void testGet() {
    // GBIF download
    var data = service.resolve(new DOI("10.15468/dl.f14yjv"));
    // IPT dataset
    data = service.resolve(new DOI("10.15472/ciasei"));
  }

}