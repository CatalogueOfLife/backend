package life.catalogue.doi.service;

import life.catalogue.api.jackson.ApiModule;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import life.catalogue.api.model.DOI;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.logging.LoggingFeature;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

import static org.junit.Assert.assertEquals;

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
    DOI doi = new DOI("10.15468/dl.f14yjv");
    var data = service.resolve(doi);
    assertEquals("Occurrence Download", data.getTitles().get(0).getTitle());
    assertEquals(doi.getPrefix(), data.getPrefix());
    assertEquals(doi.getSuffix(), data.getSuffix());
    assertEquals(doi, data.getDoi());
    assertEquals("The Global Biodiversity Information Facility", data.getPublisher());
    assertEquals("https://www.gbif.org/occurrence/download/0006447-200221144449610", data.getUrl());
    assertEquals(1, data.getCreators().size());
    assertEquals("Occdownload Gbif.Org", data.getCreators().get(0).getName());
    // IPT dataset
    doi = new DOI("10.15472/ciasei");
    data = service.resolve(doi);
    assertEquals("Colección Mastozoológica del Museo de Historia Natural de la Universidad del Cauca", data.getTitles().get(0).getTitle());
    assertEquals(doi.getPrefix(), data.getPrefix());
    assertEquals(doi.getSuffix(), data.getSuffix());
    assertEquals(doi, data.getDoi());
    assertEquals("http://ipt.biodiversidad.co/sib/resource?r=251-mhnuc-m", data.getUrl());
    assertEquals(6, data.getCreators().size());
  }

}