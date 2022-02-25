package life.catalogue.doi.service;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.DOI;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.common.util.YamlUtils;
import life.catalogue.doi.datacite.model.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.logging.LoggingFeature;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

import static org.junit.Assert.*;

@Ignore("Using real DataCite API - manual test only")
public class DataCiteServiceIT {
  static final Logger LOG = Logger.getLogger(DataCiteServiceIT.class.getName());

  DataCiteService service;
  DataCiteService prodReadService;
  Client client;

  Set<DOI> dois = new HashSet<>();

  @Before
  public void setup() throws IOException {
    final JacksonJsonProvider jacksonJsonProvider = new JacksonJaxbJsonProvider(ApiModule.MAPPER, JacksonJaxbJsonProvider.DEFAULT_ANNOTATIONS);
    ClientConfig cfg = new ClientConfig(jacksonJsonProvider);
    cfg.register(new LoggingFeature(Logger.getLogger(getClass().getName()), Level.ALL, LoggingFeature.Verbosity.PAYLOAD_ANY, 1024));
    cfg.register(new UserAgentFilter());

    // debug logging of requests
    //Handler handlerObj = new ConsoleHandler();
    //handlerObj.setLevel(Level.ALL);
    //LOG.addHandler(handlerObj);
    //LOG.setLevel(Level.ALL);
    //LOG.setUseParentHandlers(false);
    //Feature logFeature = new LoggingFeature(LOG, Level.INFO, null, null);
    //cfg.register(logFeature);

    client = ClientBuilder.newClient(cfg);

    DoiConfig doiCfg = YamlUtils.read(DoiConfig.class, "/datacite.yaml");
    service = new DataCiteService(doiCfg, client);

    DoiConfig prodCfg = new DoiConfig();
    prodCfg.api = "https://api.datacite.org";
    prodCfg.prefix = "10.x";
    prodReadService = new DataCiteService(prodCfg, client);
  }

  @After
  public void clear() throws Exception {
    for (DOI doi : dois) {
      try {
        service.delete(doi);
      } catch (DoiException e) {
        e.printStackTrace();
      }
    }
  }

  DoiAttributes generate(DOI doi) {
    DoiAttributes attr = new DoiAttributes(doi);
    attr.setTitles(List.of(new Title("Also sprach Zarathustra")));
    attr.setCreators(List.of(
      new Creator("Bang Boom Bang", NameType.ORGANIZATIONAL),
      new Creator("Stefan", "Zweig", "0000-0000-0000-0001")
    ));
    attr.setUrl("https://www.catalogueoflife.org/"+doi.getSuffix());
    attr.setPublisher("GBIF");
    attr.setPublicationYear(1988);
    attr.setDescriptions(List.of(new Description("bla bla bla bla bla bla bla")));
    return attr;
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

  @Test
  public void get404() {
    DOI doi = DOI.col("2w34edrftg7bznd3");
    var data = prodReadService.resolve(doi);
    assertNull(data);
  }

  @Test
  public void buildDOI() {
    System.out.println( service.fromDataset(1010) );
    System.out.println( service.fromDataset(2298) );
    System.out.println( service.fromDataset(25298) );
    System.out.println( service.fromDatasetSource(2298, 1010) );
    System.out.println( service.fromDatasetSource(25298, 41010) );
  }

  @Test
  public void createAndUpdate() throws Exception {
    DOI doi = DOI.test("DataCiteServiceIT-"+ UUID.randomUUID());
    dois.add(doi);

    DoiAttributes attr = generate(doi);
    service.create(attr);

    LOG.info("Update " + doi);
    attr.setTitles(List.of(new Title("Also kracht Zarathustra")));
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
    //attr.setTypes();
    attr.setUpdated("now");
    attr.setVersion("v1.3");
    LOG.info("Update complete " + doi);
    service.update(attr);
  }

  @Test
  public void publish() throws Exception {
    publishDOI();
  }

  public DOI publishDOI() throws Exception {
    DOI doi = DOI.test("DataCiteServiceIT-"+ UUID.randomUUID());
    dois.add(doi);
    DoiAttributes attr = generate(doi);
    service.create(attr);
    service.publish(doi);
    return doi;
  }

  @Test
  @Ignore("can be used to manually publish dois from a list")
  public void publishList() throws Exception {
    DoiConfig cfg = new DoiConfig();
    cfg.api = "https://api.datacite.org";
    cfg.prefix = "10.xxx";
    cfg.username = "";
    cfg.password = "";
    service = new DataCiteService(cfg, client);

    File src = new File("/Users/markus/Downloads/dois.txt");
    UTF8IoUtils.readerFromFile(src).lines().forEach(line -> {
      try {
        DOI doi = new DOI(line);
        var data = service.resolve(doi);
        System.out.println("DOI "+doi+" state: " + data.getState());
        System.out.println("  Location: "+data.getUrl());
        if (data.getState() != DoiState.FINDABLE) {
          System.out.println("  publish "+doi);
          service.publish(doi);
        }
      } catch (DoiException e) {
        e.printStackTrace();
      }
    });
  }

  @Test
  public void updateUrl() throws Exception {
    DOI doi = DOI.test("DataCiteServiceIT-"+ UUID.randomUUID());
    dois.add(doi);
    DoiAttributes attr = generate(doi);
    service.create(attr);

    LOG.info("Update " + doi);
    service.update(doi, URI.create("https://www.catalogueoflife.org/"+doi.getSuffix()));
  }

  @Test
  public void deleteDraft() throws Exception {
    DOI doi = DOI.test("DataCiteServiceIT-"+ UUID.randomUUID());
    dois.add(doi);
    DoiAttributes attr = generate(doi);
    service.create(attr);

    LOG.info("Delete " + doi);
    assertTrue(service.delete(doi));

    var resp = service.resolve(doi);
    assertNull(resp);
  }

  @Test
  public void deletePublished() throws Exception {
    DOI doi = publishDOI();

    var x = service.resolve(doi);
    assertEquals(DoiState.FINDABLE, x.getState());
    assertEquals(doi, x.getDoi());

    assertFalse(service.delete(doi));

    var resp = service.resolve(doi);
    assertNotNull(resp);

    assertEquals(DoiState.REGISTERED, resp.getState());
  }
}