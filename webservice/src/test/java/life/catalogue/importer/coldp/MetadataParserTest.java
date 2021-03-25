package life.catalogue.importer.coldp;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.Organisation;
import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.Country;
import life.catalogue.api.vocab.Gazetteer;
import life.catalogue.api.vocab.License;
import life.catalogue.common.io.Resources;
import org.gbif.nameparser.api.NomCode;
import org.junit.Test;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

public class MetadataParserTest {

  @Test
  public void milliBase() throws Exception {
    Optional<DatasetWithSettings> m = MetadataParser.readMetadata(Resources.stream("metadata/worms-MilliBase.yaml"));
    DatasetWithSettings d = m.get();
    assertEquals("MilliBase", d.getTitle());
    assertEquals("WoRMS_Myriapoda", d.getAlias());
    assertEquals("MilliBase is a global taxonomic database, managed by a group of diplopod experts that aims to capture all described millipede, pauropod and symphylan species with the associated literature, the authorities and original descriptions of species, genera and all units of higher classification", d.getDescription());
    assertEquals(List.of(
        new Organisation("Field Museum of Natural History"),
        new Organisation("Bavarian State Collection of Zoology (ZSM)")
      ), d.getOrganisations());
    assertEquals(new Person(null, null, "info@marinespecies.org", null), d.getContact());
    assertEquals(List.of(
        new Person("Sierwald, P."),
        new Person("Spelda, J.")
      ), d.getAuthors());
    assertEquals(Collections.emptyList(), d.getEditors());
    assertEquals(License.CC_BY_NC, d.getLicense());
    assertEquals("ver. (02/2021)", d.getVersion());
    assertEquals(LocalDate.of(2021, 2, 3), d.getReleased());
    assertEquals(URI.create("http://www.millibase.org"), d.getWebsite());
    assertEquals(URI.create("http://www.millibase.org/images/logo_sp2000.jpg"), d.getLogo());
    assertEquals("Sierwald, P.; Spelda, J. (2021). MilliBase. Accessed at http://www.millibase.org on 2021-02-03. doi:10.14284/370", d.getCitation());
    assertEquals(Gazetteer.MRGID, d.getGazetteer());
    assertNull(d.getCompleteness());
    assertNull(d.getConfidence());
    assertNull(d.getCode());
  }

  @Test
  public void cycad() throws Exception {
    Optional<DatasetWithSettings> m = MetadataParser.readMetadata(Resources.stream("metadata/cycads.yaml"));
    DatasetWithSettings d = m.get();
    assertEquals("The World List of Cycads, online edition", d.getTitle());
    assertEquals("Cycad List", d.getAlias());
    assertEquals("The World List of Cycads is a working list of known cycad species names with the primary goal of providing reliable information on the taxonomy of cycads for use by researchers, conservation planners, and others. It is developed in close collaboration with world's foremost cycad experts and published under the auspices of the IUCN's Cycad Specialist Group. The printed edition is published in the proceedings of the International Conference of Cycad Biology, which is held every three years.", d.getDescription());
    assertEquals(new Person("Michael", "Calonje", "michaelc@montgomerybotanical.org", null), d.getContact());
    assertEquals(License.UNSPECIFIED, d.getLicense());
    assertEquals("ver. (02/2019)", d.getVersion());
    assertEquals(LocalDate.of(2019, 2, 15), d.getReleased());
    assertEquals(URI.create("http://cycadlist.org"), d.getWebsite());
    assertEquals(URI.create("http://www.catalogueoflife.org/col/images/databases/The_World_List_of_Cycads.png"), d.getLogo());
    assertEquals("Calonje M., Stanberg L. & Stevenson, D. (eds) (2019). The World List of Cycads, online edition (version 02/2019).", d.getCitation());
    assertEquals(100, (int) d.getCompleteness());
    assertEquals(5, (int) d.getConfidence());
    assertEquals(NomCode.BOTANICAL, d.getCode());
    assertEquals(Gazetteer.ISO, d.getGazetteer());

    List<Organisation> orgs = List.of(
      new Organisation("Montgomery Botanical Center", "IUCN / SSC Cycad Specialist Group", "Coral Gables", "FL", Country.UNITED_STATES),
      new Organisation("New York Botanical Garden", null, "Bronx NY", null, Country.UNITED_STATES),
      new Organisation("Royal Botanic Gardens", "IUCN / SSC Cycad Specialist Group", "Sydney", "New South Wales", Country.AUSTRALIA)
    );
    assertEquals(orgs, d.getOrganisations());
  
    List<Person> authors = List.of(
      new Person("Michael","Calonje", "michaelc@montgomerybotanical.org", "0000-0001-9650-3136"),
      new Person("Leonie", "Stanberg"),
      new Person("Dennis", "Stevenson",null, "0000-0002-2986-7076")
    );
    assertEquals(authors, d.getAuthors());
    assertEquals(authors, d.getEditors());
  }

  @Test
  public void cycadStrings() throws Exception {
    Optional<DatasetWithSettings> m = MetadataParser.readMetadata(Resources.stream("metadata/cycadsStrings.yaml"));
    DatasetWithSettings d = m.get();
    assertEquals("The World List of Cycads, online edition", d.getTitle());
    assertEquals("Cycad List", d.getAlias());
    assertEquals("The World List of Cycads is a working list of known cycad species names with the primary goal of providing reliable information on the taxonomy of cycads for use by researchers, conservation planners, and others. It is developed in close collaboration with world's foremost cycad experts and published under the auspices of the IUCN's Cycad Specialist Group. The printed edition is published in the proceedings of the International Conference of Cycad Biology, which is held every three years.", d.getDescription());
    assertEquals(new Person("Michael", "Calonje", "michaelc@montgomerybotanical.org", null), d.getContact());
    assertEquals(License.UNSPECIFIED, d.getLicense());
    assertEquals("ver. (02/2019)", d.getVersion());
    assertEquals(LocalDate.of(2019, 2, 15), d.getReleased());
    assertEquals(URI.create("http://cycadlist.org"), d.getWebsite());
    assertEquals(URI.create("http://www.catalogueoflife.org/col/images/databases/The_World_List_of_Cycads.png"), d.getLogo());
    assertEquals("Calonje M., Stanberg L. & Stevenson, D. (eds) (2019). The World List of Cycads, online edition (version 02/2019).", d.getCitation());
    assertEquals(100, (int) d.getCompleteness());
    assertEquals(5, (int) d.getConfidence());
    assertEquals(NomCode.BOTANICAL, d.getCode());
    assertEquals(Gazetteer.ISO, d.getGazetteer());

    List<Organisation> orgs = Organisation.parse("IUCN / SSC Cycad Specialist Group, Montgomery Botanical Center, Coral Gables, FL, USA",
      "New York Botanical Garden, Bronx NY, USA",
      "Royal Botanic Gardens, Sydney, New South Wales, Australia");
    assertEquals(orgs, d.getOrganisations());

    List<Person> authors = List.of(
      new Person("Michael","Calonje"),
      new Person("Leonie", "Stanberg"),
      new Person("Dennis", "Stevenson")
    );
    assertEquals(authors, d.getAuthors());
    assertEquals(authors, d.getEditors());
  }

  @Test
  public void alucitoidea() throws Exception {
    Optional<DatasetWithSettings> m = MetadataParser.readMetadata(Resources.stream("metadata/Alucitoidea.yaml"));
    DatasetWithSettings d = m.get();

    Person donald = new Person("Donald","Hobern","dhobern@gmail.com","0000-0001-6492-4016");

    assertEquals("Catalogue of the Alucitoidea of the World", d.getTitle());
    assertEquals("Alucitoidea", d.getAlias());
    assertEquals("This GSD is derived from C. Gielis (2003) Pterophoroidea & Alucitoidea (Lepidoptera) (World Catalogue of Insects, volume 4), as subsequently maintained and updated by Cees Gielis. The database has been edited for inclusion in the Catalogue of Life and updated by Donald Hobern. A current version of the dataset is presented as a web document at https://hobern.net.Alucitoidea.html. Version 1.0 includes updates to reflect recent changes in taxonomy and new species.", d.getDescription());
    assertEquals(donald, d.getContact());
    assertEquals(License.CC_BY, d.getLicense());
    assertEquals("ver. 1.0 (09/2020)", d.getVersion());
    assertEquals(LocalDate.of(2020, 9, 18), d.getReleased());
    assertNull(d.getWebsite());
    assertEquals(URI.create("https://hobern.net/img/Alucita_hexadactyla.png"), d.getLogo());
    assertEquals("Gielis C. & Hobern D. (eds) (2020). Catalogue of the Alucitoidea of the World (version 1.0, 09/2020).", d.getCitation());
    assertNull(d.getCompleteness());
    assertNull(d.getConfidence());
    assertEquals(NomCode.ZOOLOGICAL, d.getCode());
    assertEquals(Gazetteer.ISO, d.getGazetteer());

    assertTrue(d.getOrganisations().isEmpty());

    List<Person> authors = new ArrayList<>();
    authors.add(donald);
    authors.add(new Person("Cees", "Gielis", null, "0000-0003-0857-1679"));
    assertEquals(authors, d.getAuthors());
    assertTrue(d.getEditors().isEmpty());
  }

  /**
   * we try to support simple strings given for person and organisation instead of complex types.
   */
  @Test
  public void simple() throws Exception {
    Optional<DatasetWithSettings> m = MetadataParser.readMetadata(Resources.stream("metadata/simple-person.yaml"));
    Dataset d = m.get().getDataset();

    Person rainer = new Person("Rainer","Froese","rainer@mailinator.com",null);
    Person daniel = new Person("Daniel","Pauly");

    assertEquals(rainer, d.getContact());
    assertEquals(List.of(rainer, daniel), d.getAuthors());

    Organisation o1 = new Organisation("The WorldFish Center");
    Organisation o2 = new Organisation("University of British Columbia, Canada");
    Organisation o3 = new Organisation("Food and Agriculture Organization of the United Nations; Rome; Italy");
    assertEquals(List.of(o1, o2, o3), d.getOrganisations());

    assertEquals("Fishes", d.getGroup());

  }
}