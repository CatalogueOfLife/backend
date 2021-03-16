package life.catalogue.importer.dwca;

import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.Organisation;
import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.Country;
import life.catalogue.api.vocab.License;
import life.catalogue.parser.CountryParser;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.units.qual.A;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

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
    EmlParser.Agent ag = new EmlParser.Agent();

    ag.surName="Foo";
    ag.userId="1234";
    assertNull(ag.person().get().getOrcid());

    ag.userId="0000-0001-7757-1889";
    assertEquals(ag.userId, ag.person().get().getOrcid());

    ag.userId="0000-0001-7757-1889-1234";
    assertNull(ag.person().get().getOrcid());

    ag.userId="0001-7757-1889";
    assertNull(ag.person().get().getOrcid());

    ag.userId="orcid:0000-0001-7757-1889";
    assertEquals("0000-0001-7757-1889", ag.person().get().getOrcid());

    ag.userId="https://orcid.org/0000-0001-7757-1889";
    assertEquals("0000-0001-7757-1889", ag.person().get().getOrcid());

    ag.userId="http://orcid.org/0000-0001-7757-1889";
    assertEquals("0000-0001-7757-1889", ag.person().get().getOrcid());
  }

  @Test
  public void famous() throws Exception {
    DatasetWithSettings d = read("famous.xml");
    Person markus = new Person("Markus", "Döring", "mdoering@gbif.org", "0000-0001-7757-1889");
    Person bouchard = new Person("Patrice", "Bouchard");

    assertEquals("Species named after famous people", d.getTitle());
    assertEquals("A list of species named after famous people including musicians and politicians.", d.getDescription());
    assertEquals("https://github.com/mdoering/famous-organism", d.getWebsite().toString());
    //assertEquals("Species named after famous people", d.getLicense());
    assertEquals(markus, d.getContact());
    assertEquals(List.of(markus, bouchard), d.getAuthors());
    assertEquals("2017-01-19", d.getReleased().toString());
    assertEquals("http://www.marinespecies.org/aphia.php?p=taxdetails&id=146230", d.getLogo().toString());
    assertEquals("cite my famous dataset", d.getCitation());
    assertEquals("Famous People", d.getAlias());
  }
  
  @Test
  public void vascan() throws Exception {
    DatasetWithSettings d = read("vascan.xml");
    
    assertEquals("Database of Vascular Plants of Canada (VASCAN)", d.getTitle());
    assertEquals("The Database of Vascular Plants of Canada or VASCAN (http://data.canadensys.net/vascan) is a comprehensive and curated checklist of all vascular plants reported in Canada, Greenland (Denmark), and Saint Pierre and Miquelon (France). VASCAN was developed at the Université de Montréal Biodiversity Centre and is maintained by a group of editors and contributors. For every core taxon in the checklist (species, subspecies, or variety), VASCAN provides the accepted scientific name, the accepted French and English vernacular names, and their synonyms/alternatives in Canada, as well as the distribution status (native, introduced, ephemeral, excluded, extirpated, doubtful or absent) of the plant for each province or territory, and the habit (tree, shrub, herb and/or vine) of the plant in Canada. For reported hybrids (nothotaxa or hybrid formulas) VASCAN also provides the hybrid parents, except if the parents of the hybrid do notoccur in Canada. All taxa are linked to a classification. VASCAN refers to a source for all name, classification and distribution information. All data have been released to the public domain under a CC0 waiver and are available through Canadensys and the Global Biodiversity Information Facility (GBIF). VASCAN is a service to the scientific community and the general public, including administrations, companies, and non-governmental organizations.", d.getDescription());
    assertEquals("http://data.canadensys.net/vascan/", d.getWebsite().toString());
    assertEquals(License.CC0, d.getLicense());
    assertEquals("Brouillet L.", d.getContact().toString());
    assertEquals("[Brouillet L.]", d.getAuthors().toString());
    assertEquals("2017-12-18", d.getReleased().toString());
  }
  
  @Test
  public void worms() throws Exception {
    DatasetWithSettings d = read("worms-eml.xml");
    
    assertEquals("World Register of Marine Species", d.getTitle());
    assertEquals("An authoritative classification and catalogue of marine names", d.getDescription());
    assertEquals(new Person(null, null, "info@marinespecies.org", null), d.getContact());
    assertEquals(List.of(pmail("info@marinespecies.org")), d.getAuthors());
    assertEquals(List.of(
      p("Vandepitte", "Leen", "leen.vandepitte@vliz.be"),
      p("Horton", "Tammy", "tammy.horton@noc.ac.uk"),
      p("Kroh", "Andreas", "andreas.kroh@nhm-wien.ac.at"),
      p("Ahyong", "Shane", "Shane.Ahyong@austmus.gov.au"),
      p("Bailly", "Nicolas", "n.bailly@q-quatics.org"),
      p("Boyko", "Christopher", "cboyko@amnh.org"),
      p("Brandão", "Simone", "brandao.sn.100@gmail.com"),
      p("Gofas", "Serge", "sgofas@uma.es"),
      p("Hooper", "John", "john.hooper@qm.qld.gov.au"),
      p("Hernandez", "Francisco", "francisco.hernandez@vliz.be"),
      p("Holovachov", "Oleksandr", "oleksandr.holovachov@nrm.se"),
      p("Mees", "Jan", "jan.mees@vliz.be"),
      p("Molodtsova", "Tina", "tina@ocean.ru"),
      p("Paulay", "Gustav", "paulay@flmnh.ufl.edu")
    ), d.getEditors());
    for (Organisation o : d.getOrganisations()) {
      System.out.println(o.quoteParts());
    }
    assertEquals(List.of(
      o("WoRMS Editorial Board"),
      o("Vlaams Instituut voor de Zee","","","Belgium"),
      o("National Oceanography Centre, Southampton; Ocean Biogeochemistry and Ecosystems", "University of Southampton","","","United Kingdom"),
      o("Natural History Museum Vienna","","","Austria"),
      o("Marine Invertebrates", "Australian Museum","","","Australia"),
      o("Quantitative Aquatics, Inc.","","","Philippines"),
      o("Division of Invertebrate Zoology", "American Museum of Natural History","","","United States of America"),
      o("Departamento de Ciências Biológicas", "Universidade Estadual de Santa Cruz","","","Brazil"),
      o("Faculty of Sciences; Departamento de Biología Animal", "University of Málaga","","","Spain"),
      o("Queensland Museum","","","Australia"),
      o("Swedish Museum of Natural History","","","Sweden"),
      o("P. P. Shirshov Institute of Oceanology", "Russian Academy of Sciences","","","Russian Federation"),
      o("Florida Museum of Natural History", "University of Florida","","","United States of America"),
      o("World Register of Marine Species")
    ), d.getOrganisations());
    assertEquals("2021-03-01", d.getReleased().toString());
    assertEquals("http://www.marinespecies.org/documents/WoRMS%20branding/WoRMS_logo_blue_negative.png", d.getLogo().toString());
    assertNull(d.getAlias());
    assertEquals("<b>WoRMS Editorial Board</b> (2021). World Register of Marine Species. Available from http://www.marinespecies.org at VLIZ. Accessed 2021-03-01. doi:10.14284/170", d.getCitation());
    assertEquals("http://www.marinespecies.org/", d.getWebsite().toString());
    assertNull(d.getLicense());
    assertEquals("World Oceans", d.getGeographicScope());
  }

  Organisation o(String name) {
    return new Organisation(name);
  }
  Organisation o(String name, String city, String state, String country) {
    return o(null, name, city, state, country);
  }
  Organisation o(String department, String name, String city, String state, String country) {
    return new Organisation(trimToNull(name), trimToNull(department), trimToNull(city), trimToNull(state), CountryParser.PARSER.parseOrNull(country));
  }

  Person p(String surname, String firstname) {
    return new Person(firstname,surname, null,null);
  }
  Person p(String surname, String firstname, String email) {
    return new Person(firstname,surname, email,null);
  }
  Person pmail(String email) {
    return new Person(null,null,email,null);
  }

}