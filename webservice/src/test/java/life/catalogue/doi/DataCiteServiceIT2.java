package life.catalogue.doi;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.DOI;
import life.catalogue.common.util.YamlUtils;
import life.catalogue.doi.datacite.model.*;
import life.catalogue.doi.service.DataCiteService;
import life.catalogue.doi.service.DataCiteWrapper;
import life.catalogue.doi.service.DoiConfig;
import life.catalogue.doi.service.DoiException;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import javax.ws.rs.client.Client;

import life.catalogue.dw.jersey.JerseyClientRule;

import org.junit.*;

import static org.junit.Assert.*;

/**
 * Same test again as in doi package, but using the Dropwizard jersey client
 * which apparently behaves differently and uses http client under the hood.
 */
@Ignore("Using real DataCite API - manual test only")
public class DataCiteServiceIT2 {
  static final Logger LOG = Logger.getLogger(DataCiteServiceIT2.class.getName());

  DataCiteService service;
  DataCiteService prodReadService;

  @Rule
  public JerseyClientRule jerseyClientRule = new JerseyClientRule();

  Set<DOI> dois = new HashSet<>();

  @Before
  public void setup() throws IOException {
    final Client client = jerseyClientRule.getClient();

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
  public void jsonDoiSerde() throws Exception {
    DoiAttributes attr = generate(DOI.test("1234567890"));
    DataCiteWrapper data = new DataCiteWrapper(attr);
    System.out.println(ApiModule.MAPPER.writeValueAsString(data));
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