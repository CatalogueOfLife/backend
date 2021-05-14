package life.catalogue.doi.service;

import life.catalogue.api.jackson.ApiModule;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Feature;

import life.catalogue.api.model.DOI;

import life.catalogue.common.util.YamlUtils;

import life.catalogue.doi.datacite.model.*;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.logging.LoggingFeature;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@Ignore("Using real DataCite API - manual test only")
public class DataCiteServiceIT {
  static final Logger LOGGER = Logger.getLogger(DataCiteServiceIT.class.getName());

  DataCiteService service;
  DataCiteService prodReadService;

  @Before
  public void setup() throws IOException {
    final JacksonJsonProvider jacksonJsonProvider = new JacksonJaxbJsonProvider(ApiModule.MAPPER, JacksonJaxbJsonProvider.DEFAULT_ANNOTATIONS);
    ClientConfig cfg = new ClientConfig(jacksonJsonProvider);
    cfg.register(new LoggingFeature(Logger.getLogger(getClass().getName()), Level.ALL, LoggingFeature.Verbosity.PAYLOAD_ANY, 1024));
    cfg.register(new UserAgentFilter());

    // debug logging of requests
    Handler handlerObj = new ConsoleHandler();
    handlerObj.setLevel(Level.ALL);
    LOGGER.addHandler(handlerObj);
    LOGGER.setLevel(Level.ALL);
    LOGGER.setUseParentHandlers(false);
    Feature logFeature = new LoggingFeature(LOGGER, Level.INFO, null, null);
    cfg.register(logFeature);

    final Client client = ClientBuilder.newClient(cfg);

    DoiConfig doiCfg = YamlUtils.read(DoiConfig.class, "/datacite.yaml");
    service = new DataCiteService(doiCfg, client);

    DoiConfig prodCfg = new DoiConfig();
    prodCfg.api = "https://api.datacite.org";
    prodCfg.prefix = "10.x";
    prodReadService = new DataCiteService(prodCfg, client);
  }

  @Test
  public void testGet() {
    // GBIF download
    DOI doi = new DOI("10.15468/dl.f14yjv");
    var data = prodReadService.resolve(doi);
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
    data = prodReadService.resolve(doi);
    assertEquals("Colección Mastozoológica del Museo de Historia Natural de la Universidad del Cauca", data.getTitles().get(0).getTitle());
    assertEquals(doi.getPrefix(), data.getPrefix());
    assertEquals(doi.getSuffix(), data.getSuffix());
    assertEquals(doi, data.getDoi());
    assertEquals("http://ipt.biodiversidad.co/sib/resource?r=251-mhnuc-m", data.getUrl());
    assertEquals(6, data.getCreators().size());
  }

  @Test(expected = NotFoundException.class)
  public void get404() {
    // GBIF download
    DOI doi = DOI.col("2w34edrftg7bznd3");
    var data = prodReadService.resolve(doi);
  }

  @Test
  public void createAndUpdate() throws Exception {
    DOI doi = DOI.test("DataCiteServiceIT-"+ UUID.randomUUID());
    service.create(doi);

    DoiAttributes attr = new DoiAttributes(doi);
    attr.setTitles(List.of(new Title("Also sprach Zarathustra")));
    attr.setCreators(List.of(
      new Creator("Bang Boom Bang", NameType.ORGANIZATIONAL),
      new Creator("Stefan", "Zweig")
    ));
    LOGGER.info("Update " + doi);
    service.update(attr);

    attr.setPublicationYear(1988);
    attr.setUrl("https://www.catalogueoflife.org/"+doi.getSuffix());
    attr.setCreated("created");
    attr.setContributors(List.of(
      new Contributor("Bang Boom Bang", NameType.ORGANIZATIONAL, ContributorType.DATA_MANAGER),
      new Contributor("Stefan", "Zweig", ContributorType.RESEARCHER)
    ));
    attr.setContentUrl(List.of("https://www.catalogueoflife.org/content/"+doi.getSuffix()));
    attr.setDates(List.of(new Date("1988", DateType.COLLECTED)));
    attr.setDescriptions(List.of(new Description("bla bla bla bla bla bla bla")));
    //attr.setEvent();
    attr.setFormats(List.of("ColDP"));
    attr.setGeoLocations(List.of(new GeoLocation("Israel")));
    attr.setIdentifiers(List.of(new Identifier("1234", "internal"), new Identifier(DOI.test("f32"))));
    attr.setLanguage("eng");
    attr.setMetadataVersion(4.0f);
    attr.setPublisher("GBIF");
    attr.setReason("no reason is good enough");
    attr.setRegistered("1999-12-31");
    attr.setRelatedIdentifiers(List.of(new RelatedIdentifier(DOI.test("x961"),RelationType.CITES,ResourceType.DATA_PAPER)));
    attr.setRightsList(List.of(new Rights("rcctzew cuhe wiuchew")));
    attr.setSizes(List.of("532231"));
    attr.setSource("source");
    attr.setSchemaVersion("schema version");
    attr.setSubjects(List.of(new Subject("gbif"), new Subject("col"), new Subject("biodiversity")));
    attr.setType(ResourceType.DATASET);
    //attr.setTypes();
    attr.setUpdated("now");
    attr.setVersion("v1.3");
    LOGGER.info("Update complete " + doi);
    service.update(attr);
  }

  @Test
  public void updateUrl() throws Exception {
    DOI doi = DOI.test("DataCiteServiceIT-"+ UUID.randomUUID());
    service.create(doi);

    LOGGER.info("Update " + doi);
    service.update(doi, URI.create("https://www.catalogueoflife.org/"+doi.getSuffix()));
  }

  @Test
  public void delete() throws Exception {
    DOI doi = DOI.test("DataCiteServiceIT-"+ UUID.randomUUID());
    service.create(doi);

    LOGGER.info("Delete " + doi);
    service.delete(doi);

    var resp = service.resolve(doi);
    assertNull(resp);
  }
}