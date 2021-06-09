package life.catalogue.importer.dwca;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.vocab.Country;
import life.catalogue.api.vocab.License;

import java.io.IOException;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 *
 */
public class EmlParserTest {

  private DatasetWithSettings read(String name) throws IOException {
    return EmlParser.parse(getClass().getResourceAsStream("/metadata/" + name)).get();
  }

  @Test
  public void orcidParsing() throws Exception {
    EmlParser.EmlAgent ag = new EmlParser.EmlAgent();

    ag.surName="Foo";
    ag.userId="1234";
    assertNull(ag.agent(false).get().getOrcid());

    ag.userId="0000-0001-7757-1889";
    assertEquals(ag.userId, ag.agent(false).get().getOrcid());

    ag.userId="0000-0001-7757-1889-1234";
    assertNull(ag.agent(false).get().getOrcid());

    ag.userId="0001-7757-1889";
    assertNull(ag.agent(false).get().getOrcid());

    ag.userId="orcid:0000-0001-7757-1889";
    assertEquals("0000-0001-7757-1889", ag.agent(false).get().getOrcid());

    ag.userId="https://orcid.org/0000-0001-7757-1889";
    assertEquals("0000-0001-7757-1889", ag.agent(false).get().getOrcid());

    ag.userId="http://orcid.org/0000-0001-7757-1889";
    assertEquals("0000-0001-7757-1889", ag.agent(false).get().getOrcid());
  }

  @Test
  public void famous() throws Exception {
    DatasetWithSettings d = read("famous.xml");
    Agent markus = new Agent("Markus", "Döring", "mdoering@gbif.org", "0000-0001-7757-1889");
    markus.setOrganisation("GBIF");
    Agent bouchard = new Agent("Patrice", "Bouchard");

    assertEquals("Species named after famous people", d.getTitle());
    assertEquals("A list of species named after famous people including musicians and politicians.", d.getDescription());
    assertEquals("https://github.com/mdoering/famous-organism", d.getUrl().toString());
    //assertEquals("Species named after famous people", d.getLicense());
    assertEquals(markus, d.getContact());
    assertEquals(List.of(markus, bouchard), d.getCreator());
    assertEquals("2017-01-19", d.getIssued().toString());
    assertEquals("http://www.marinespecies.org/aphia.php?p=taxdetails&id=146230", d.getLogo().toString());
    assertEquals("Famous People", d.getAlias());
  }
  
  @Test
  public void vascan() throws Exception {
    DatasetWithSettings d = read("vascan.xml");
    
    assertEquals("Database of Vascular Plants of Canada (VASCAN)", d.getTitle());
    assertEquals("The Database of Vascular Plants of Canada or VASCAN (http://data.canadensys.net/vascan) is a comprehensive and curated checklist of all vascular plants reported in Canada, Greenland (Denmark), and Saint Pierre and Miquelon (France). VASCAN was developed at the Université de Montréal Biodiversity Centre and is maintained by a group of editors and contributors. For every core taxon in the checklist (species, subspecies, or variety), VASCAN provides the accepted scientific name, the accepted French and English vernacular names, and their synonyms/alternatives in Canada, as well as the distribution status (native, introduced, ephemeral, excluded, extirpated, doubtful or absent) of the plant for each province or territory, and the habit (tree, shrub, herb and/or vine) of the plant in Canada. For reported hybrids (nothotaxa or hybrid formulas) VASCAN also provides the hybrid parents, except if the parents of the hybrid do notoccur in Canada. All taxa are linked to a classification. VASCAN refers to a source for all name, classification and distribution information. All data have been released to the public domain under a CC0 waiver and are available through Canadensys and the Global Biodiversity Information Facility (GBIF). VASCAN is a service to the scientific community and the general public, including administrations, companies, and non-governmental organizations.", d.getDescription());
    assertEquals("http://data.canadensys.net/vascan/", d.getUrl().toString());
    assertEquals(License.CC0, d.getLicense());
    assertEquals("Brouillet L.", d.getContact().toString());
    assertEquals("[Brouillet L.]", d.getCreator().toString());
    assertEquals("2017-12-18", d.getIssued().toString());
  }
  
  @Test
  public void worms() throws Exception {
    DatasetWithSettings d = read("worms-eml.xml");
    
    assertEquals("World Register of Marine Species", d.getTitle());
    assertEquals("An authoritative classification and catalogue of marine names", d.getDescription());
    assertEquals(o("World Register of Marine Species", "info@marinespecies.org", null), d.getContact());
    assertEquals(List.of(
      o("WoRMS Editorial Board", "info@marinespecies.org", "http://www.marinespecies.org")
    ), d.getCreator());
    assertEquals(List.of(
      p("Horton", "Tammy", "tammy.horton@noc.ac.uk", "University of Southampton", "National Oceanography Centre, Southampton; Ocean Biogeochemistry and Ecosystems", Country.UNITED_KINGDOM),
      p("Kroh", "Andreas", "andreas.kroh@nhm-wien.ac.at", "Natural History Museum Vienna", null, Country.AUSTRIA),
      p("Ahyong", "Shane", "Shane.Ahyong@austmus.gov.au", "Australian Museum", "Marine Invertebrates", Country.AUSTRALIA),
      p("Bailly", "Nicolas", "n.bailly@q-quatics.org", "Quantitative Aquatics, Inc.", null, Country.PHILIPPINES),
      p("Boyko", "Christopher", "cboyko@amnh.org", "American Museum of Natural History", "Division of Invertebrate Zoology", Country.UNITED_STATES),
      p("Brandão", "Simone", "brandao.sn.100@gmail.com", "Universidade Estadual de Santa Cruz", "Departamento de Ciências Biológicas", Country.BRAZIL),
      p("Gofas", "Serge", "sgofas@uma.es", "University of Málaga", "Faculty of Sciences; Departamento de Biología Animal", Country.SPAIN),
      p("Hooper", "John", "john.hooper@qm.qld.gov.au", "Queensland Museum", null, Country.AUSTRALIA),
      p("Hernandez", "Francisco", "francisco.hernandez@vliz.be", "Vlaams Instituut voor de Zee", null, Country.BELGIUM),
      p("Holovachov", "Oleksandr", "oleksandr.holovachov@nrm.se", "Swedish Museum of Natural History", null, Country.SWEDEN),
      p("Mees", "Jan", "jan.mees@vliz.be", "Vlaams Instituut voor de Zee", null, Country.BELGIUM),
      p("Molodtsova", "Tina", "tina@ocean.ru", "Russian Academy of Sciences", "P. P. Shirshov Institute of Oceanology", Country.RUSSIAN_FEDERATION),
      p("Paulay", "Gustav", "paulay@flmnh.ufl.edu", "University of Florida", "Florida Museum of Natural History", Country.UNITED_STATES)
    ), d.getEditor());
    assertEquals(List.of(
      new Agent(null, "Leen", "Vandepitte", null, "Vlaams Instituut voor de Zee", null, null, null, Country.BELGIUM, "leen.vandepitte@vliz.be", null, "custodianSteward"),
      o("WoRMS Steering Committee", "info@marinespecies.org", null, "owner")
    ),  d.getContributor());
    assertEquals("2021-03-01", d.getIssued().toString());
    assertEquals("http://www.marinespecies.org/documents/WoRMS%20branding/WoRMS_logo_blue_negative.png", d.getLogo().toString());
    assertNull(d.getAlias());
    assertEquals("http://www.marinespecies.org/", d.getUrl().toString());
    assertNull(d.getLicense());
    assertEquals("World Oceans", d.getGeographicScope());
  }

  Agent o(String name) {
    return Agent.parse(name);
  }
  Agent o(String name, String email, String url) {
    return o(null, name, null, null, null, email, url);
  }
  Agent o(String name, String email, String url, String role) {
    Agent a = o(null, name, null, null, null, email, url);
    a.setNote(role);
    return a;
  }
  Agent o(String name, String city, String state, Country country) {
    return o(null, name, city, state, country);
  }
  Agent o(String department, String name, String city, String state, Country country) {
    return o(department, name, city, state, country, null, null);
  }
  Agent o(String department, String name, String city, String state, Country country, String email, String url) {
    return new Agent(null, trimToNull(name), trimToNull(department), trimToNull(city), trimToNull(state), country, email, url);
  }

  Agent p(String surname, String firstname) {
    return new Agent(firstname,surname, null,null);
  }
  Agent p(String surname, String firstname, String email) {
    return new Agent(firstname,surname, email,null);
  }
  Agent p(String surname, String firstname, String email, String role) {
    var a = new Agent(firstname,surname, email,null);
    a.setNote(role);
    return a;
  }
  Agent p(String surname, String firstname, String email, String organization, String department, Country country) {
    var a = new Agent(firstname,surname, email,null);
    a.setOrganisation(organization);
    a.setDepartment(department);
    a.setCountry(country);
    return a;
  }

  Agent pmail(String email) {
    return new Agent(null,null,email,null);
  }

}