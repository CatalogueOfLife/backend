package life.catalogue.metadata.coldp;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.vocab.Country;
import life.catalogue.api.vocab.Gazetteer;
import life.catalogue.api.vocab.License;
import life.catalogue.common.csl.CslUtil;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.common.io.Resources;

import org.gbif.nameparser.api.NomCode;

import java.io.FileInputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

public class MetadataParserTest {

  @Test
  public void coldpSpecs() throws Exception {
    Optional<DatasetWithSettings> m = ColdpMetadataParser.readYAML(Resources.stream("metadata/coldpspecs.yaml"));
    DatasetWithSettings d = m.get();

    assertNull(d.getType());
    assertNull(d.getDataFormat());
    assertEquals("ColDP Example. The full dataset title", d.getTitle());
    assertNotNull(d.getDescription());
    assertEquals(8, d.getContributor().size());
    assertEquals(Agent.person("Rainer", "Froese", "rainer@mailinator.com"), d.getContact());
    assertEquals(List.of(
      Agent.person("Nicolas", "Bailly", null, "0000-0003-4994-0653"),
      Agent.person("Rainer", "Froese", null, "0000-0001-9745-636X"),
      Agent.person("Daniel", "Pauly", null, "0000-0003-3756-4793")
    ), d.getCreator());
    assertEquals(List.of(
      Agent.person("Rainer", "Froese", "rainer@mailinator.com", "0000-0001-9745-636X"),
      Agent.person("Daniel", "Pauly", null, "0000-0003-3756-4793")
    ), d.getEditor());
    assertEquals(License.CC0, d.getLicense());
    assertEquals("v.48 (06/2018)", d.getVersion());
    assertEquals("2018-06-01", d.getIssued().toString());
    assertEquals("https://www.fishbase.org", d.getUrl().toString());
    assertEquals("https://www.fishbase.de/images/gifs/fblogo_new.gif", d.getLogo().toString());

    assertNull(d.getCode());
    assertEquals((Integer)5, d.getConfidence());
    assertEquals((Integer)95, d.getCompleteness());
    assertEquals("Remarks, comments and usage notes about this dataset", d.getNotes());
    assertEquals("ColDP Example", d.getAlias());

    assertEquals(6, d.getUrlFormatter().size());
    assertEquals("https://fishbase.mnhn.fr/summary/{ID}", d.getUrlFormatter().get("taxon"));
    assertEquals("https://github.com/CatalogueOfLife/coldp-generator", d.getConversion().getUrl().toString());
    assertEquals("The MySQL database is being exported on a monthly basis to ColDP CSV files with the help of a python script.", d.getConversion().getDescription());
  }

  @Test
  public void coldpSpecsCslStyle() throws Exception {
    // uses proper csl field names like container-title
    Optional<DatasetWithSettings> m = ColdpMetadataParser.readYAML(Resources.stream("metadata/coldpspecs2.yaml"));
    DatasetWithSettings d = m.get();

    assertNull(d.getType());
    assertNull(d.getDataFormat());
    assertEquals("ColDP Example. The full dataset title", d.getTitle());
    assertNotNull(d.getDescription());
    assertEquals(8, d.getContributor().size());
    assertEquals(Agent.person("Rainer", "Froese", "rainer@mailinator.com"), d.getContact());
    assertEquals(List.of(
      Agent.person("Nicolas", "Bailly", null, "0000-0003-4994-0653"),
      Agent.person("Rainer", "Froese", null, "0000-0001-9745-636X"),
      Agent.person("Daniel", "Pauly", null, "0000-0003-3756-4793")
    ), d.getCreator());
    assertEquals(List.of(
      Agent.person("Rainer", "Froese", "rainer@mailinator.com", "0000-0001-9745-636X"),
      Agent.person("Daniel", "Pauly", null, "0000-0003-3756-4793")
    ), d.getEditor());
    assertEquals(License.CC0, d.getLicense());
    assertEquals("v.48 (06/2018)", d.getVersion());
    assertEquals("2018-06-01", d.getIssued().toString());
    assertEquals("https://www.fishbase.org", d.getUrl().toString());
    assertEquals("https://www.fishbase.de/images/gifs/fblogo_new.gif", d.getLogo().toString());

    assertNull(d.getCode());
    assertEquals((Integer)5, d.getConfidence());
    assertEquals((Integer)95, d.getCompleteness());
    assertEquals("my personal,\n" +
                 "very long notes", d.getNotes());
    assertEquals("ColDP Example", d.getAlias());
  }

  @Test
  public void milliBase() throws Exception {
    Optional<DatasetWithSettings> m = ColdpMetadataParser.readYAML(Resources.stream("metadata/worms-MilliBase.yaml"));
    DatasetWithSettings d = m.get();
    assertEquals("MilliBase", d.getTitle());
    assertEquals("WoRMS_Myriapoda", d.getAlias());
    assertEquals("MilliBase is a global taxonomic database, managed by a group of diplopod experts that aims to capture all described millipede, pauropod and symphylan species with the associated literature, the authorities and original descriptions of species, genera and all units of higher classification", d.getDescription());
    assertEquals(List.of(
        Agent.parse("Field Museum of Natural History"),
        Agent.parse("Bavarian State Collection of Zoology (ZSM)")
      ), d.getContributor());
    assertEquals(Agent.person(null, null, "info@marinespecies.org"), d.getContact());
    assertEquals(List.of(
        Agent.parse("Sierwald, P."),
        Agent.parse("Spelda, J.")
      ), d.getCreator());
    assertNull(d.getEditor());
    assertEquals(License.CC_BY_NC, d.getLicense());
    assertEquals("ver. (02/2021)", d.getVersion());
    assertEquals(FuzzyDate.of(2021, 2, 3), d.getIssued());
    assertEquals(URI.create("http://www.millibase.org"), d.getUrl());
    assertEquals(URI.create("http://www.millibase.org/images/logo_sp2000.jpg"), d.getLogo());
    assertEquals(Gazetteer.MRGID, d.getGazetteer());
    assertNull(d.getCompleteness());
    assertNull(d.getConfidence());
    assertNull(d.getCode());
  }

  @Test
  public void cycad() throws Exception {
    Optional<DatasetWithSettings> m = ColdpMetadataParser.readYAML(Resources.stream("metadata/cycads.yaml"));
    DatasetWithSettings d = m.get();
    assertEquals("The World List of Cycads, online edition", d.getTitle());
    assertEquals("Cycad List", d.getAlias());
    assertEquals("The World List of Cycads is a working list of known cycad species names with the primary goal of providing reliable information on the taxonomy of cycads for use by researchers, conservation planners, and others. It is developed in close collaboration with world's foremost cycad experts and published under the auspices of the IUCN's Cycad Specialist Group. The printed edition is published in the proceedings of the International Conference of Cycad Biology, which is held every three years.", d.getDescription());
    assertEquals(Agent.person("Michael", "Calonje", "michaelc@montgomerybotanical.org"), d.getContact());
    assertEquals(License.UNSPECIFIED, d.getLicense());
    assertEquals("ver. (02/2019)", d.getVersion());
    assertEquals(FuzzyDate.of(2019, 2, 15), d.getIssued());
    assertEquals(URI.create("http://cycadlist.org"), d.getUrl());
    assertEquals(URI.create("http://www.catalogueoflife.org/col/images/databases/The_World_List_of_Cycads.png"), d.getLogo());
    assertEquals(100, (int) d.getCompleteness());
    assertEquals(5, (int) d.getConfidence());
    assertEquals(NomCode.BOTANICAL, d.getCode());
    assertEquals(Gazetteer.ISO, d.getGazetteer());

    List<Agent> orgs = List.of(
      Agent.organisation("Montgomery Botanical Center", "IUCN / SSC Cycad Specialist Group", "Coral Gables", "FL", Country.UNITED_STATES),
      Agent.organisation("New York Botanical Garden", null, "Bronx NY", null, Country.UNITED_STATES),
      Agent.organisation("Royal Botanic Gardens", "IUCN / SSC Cycad Specialist Group", "Sydney", "New South Wales", Country.AUSTRALIA)
    );
    assertEquals(orgs, d.getContributor());
  
    List<Agent> authors = List.of(
      Agent.person("Michael","Calonje", "michaelc@montgomerybotanical.org", "0000-0001-9650-3136"),
      Agent.person("Leonie", "Stanberg"),
      Agent.person("Dennis", "Stevenson",null, "0000-0002-2986-7076")
    );
    assertEquals(authors, d.getCreator());
    assertEquals(authors, d.getEditor());
  }

  @Test
  public void cycadStrings() throws Exception {
    Optional<DatasetWithSettings> m = ColdpMetadataParser.readYAML(Resources.stream("metadata/cycadsStrings.yaml"));
    DatasetWithSettings d = m.get();
    assertEquals("The World List of Cycads, online edition", d.getTitle());
    assertEquals("Cycad List", d.getAlias());
    assertEquals("The World List of Cycads is a working list of known cycad species names with the primary goal of providing reliable information on the taxonomy of cycads for use by researchers, conservation planners, and others. It is developed in close collaboration with world's foremost cycad experts and published under the auspices of the IUCN's Cycad Specialist Group. The printed edition is published in the proceedings of the International Conference of Cycad Biology, which is held every three years.", d.getDescription());
    assertEquals(Agent.person("Michael", "Calonje", "michaelc@montgomerybotanical.org"), d.getContact());
    assertEquals(License.UNSPECIFIED, d.getLicense());
    assertEquals("ver. (02/2019)", d.getVersion());
    assertEquals(FuzzyDate.of(2019, 2, 15), d.getIssued());
    assertEquals(URI.create("http://cycadlist.org"), d.getUrl());
    assertEquals(URI.create("http://www.catalogueoflife.org/col/images/databases/The_World_List_of_Cycads.png"), d.getLogo());
    assertEquals(100, (int) d.getCompleteness());
    assertEquals(5, (int) d.getConfidence());
    assertEquals(NomCode.BOTANICAL, d.getCode());
    assertEquals(Gazetteer.ISO, d.getGazetteer());

    List<Agent> orgs = Agent.parse("IUCN / SSC Cycad Specialist Group, Montgomery Botanical Center, Coral Gables, FL, USA",
      "New York Botanical Garden, Bronx NY, USA",
      "Royal Botanic Gardens, Sydney, New South Wales, Australia");
    assertEquals(orgs, d.getContributor());

    List<Agent> authors = List.of(
      new Agent("Michael","Calonje"),
      new Agent("Leonie", "Stanberg"),
      new Agent("Dennis", "Stevenson")
    );
    assertEquals(authors, d.getCreator());
    assertEquals(authors, d.getEditor());
  }

  @Test
  public void alucitoidea() throws Exception {
    Optional<DatasetWithSettings> m = ColdpMetadataParser.readYAML(Resources.stream("metadata/Alucitoidea.yaml"));
    DatasetWithSettings d = m.get();

    Agent donald = Agent.person("Donald","Hobern","dhobern@gmail.com","0000-0001-6492-4016");

    assertEquals("Catalogue of the Alucitoidea of the World", d.getTitle());
    assertEquals("Alucitoidea", d.getAlias());
    assertEquals("This GSD is derived from C. Gielis (2003) Pterophoroidea & Alucitoidea (Lepidoptera) (World Catalogue of Insects, volume 4), as subsequently maintained and updated by Cees Gielis. The database has been edited for inclusion in the Catalogue of Life and updated by Donald Hobern. A current version of the dataset is presented as a web document at https://hobern.net.Alucitoidea.html. Version 1.0 includes updates to reflect recent changes in taxonomy and new species.", d.getDescription());
    assertEquals(donald, d.getContact());
    assertEquals(License.CC_BY, d.getLicense());
    assertEquals("ver. 1.0 (09/2020)", d.getVersion());
    assertEquals(FuzzyDate.of(2020, 9, 18), d.getIssued());
    assertNull(d.getUrl());
    assertEquals(URI.create("https://hobern.net/img/Alucita_hexadactyla.png"), d.getLogo());
    assertNull(d.getCompleteness());
    assertNull(d.getConfidence());
    assertEquals(NomCode.ZOOLOGICAL, d.getCode());
    assertEquals(Gazetteer.ISO, d.getGazetteer());

    assertNull(d.getContributor());

    List<Agent> authors = new ArrayList<>();
    authors.add(donald);
    authors.add(Agent.person("Cees", "Gielis", null, "0000-0003-0857-1679"));
    assertEquals(authors, d.getCreator());
    assertNull(d.getEditor());
  }

  @Test
  public void alucitoideaWithHtml() throws Exception {
    Optional<DatasetWithSettings> m = ColdpMetadataParser.readYAML(Resources.stream("metadata/AlucitoideaWithHtml.yaml"));
    DatasetWithSettings d = m.get();

    Agent donald = Agent.person("Donald","Hobern","dhobern@gmail.com","0000-0001-6492-4016");

    assertEquals("Catalogue of the Alucitoidea of the World", d.getTitle());
    assertEquals("Alucitoidea", d.getAlias());
    assertEquals("This GSD is derived from C. Gielis (2003) Pterophoroidea & Alucitoidea (Lepidoptera) (World Catalogue of Insects, volume 4), as subsequently maintained and updated by Cees Gielis."
                 + "\nThe database has been edited for inclusion in the Catalogue of Life and updated by Donald Hobern."
                 + "\nA current version of the dataset is presented as a web document at https://hobern.net.Alucitoidea.html."
                 + "\nVersion 1.0 includes updates to reflect recent changes in taxonomy and new species.", d.getDescription());
    assertEquals(donald, d.getContact());
    assertEquals(License.CC_BY, d.getLicense());
    assertEquals("ver. 1.0 (09/2020)", d.getVersion());
    assertEquals(FuzzyDate.of(2020, 9, 18), d.getIssued());
    assertNull(d.getUrl());
    assertEquals(URI.create("https://hobern.net/img/Alucita_hexadactyla.png"), d.getLogo());
    assertNull(d.getCompleteness());
    assertNull(d.getConfidence());
    assertEquals(NomCode.ZOOLOGICAL, d.getCode());
    assertEquals(Gazetteer.ISO, d.getGazetteer());

    assertNull(d.getContributor());

    List<Agent> authors = new ArrayList<>();
    authors.add(donald);
    authors.add(Agent.person("Cees", "Gielis", null, "0000-0003-0857-1679"));
    assertEquals(authors, d.getCreator());
    assertNull(d.getEditor());
  }

  /**
   * we try to support simple strings given for person and organisation instead of complex types.
   */
  @Test
  public void simple() throws Exception {
    Optional<DatasetWithSettings> m = ColdpMetadataParser.readYAML(Resources.stream("metadata/simple-person.yaml"));
    Dataset d = m.get().getDataset();

    Agent rainer = Agent.person("Rainer","Froese","rainer@mailinator.com");
    Agent daniel = Agent.person("Daniel","Pauly");

    assertEquals(rainer, d.getContact());
    assertEquals(List.of(rainer, daniel), d.getCreator());

    Agent o1 = Agent.parse("The WorldFish Center");
    Agent o2 = Agent.parse("University of British Columbia, Canada");
    Agent o3 = Agent.parse("Food and Agriculture Organization of the United Nations; Rome; Italy");
    assertEquals(List.of(o1, o2, o3), d.getContributor());

    assertEquals("Fishes", d.getTaxonomicScope());
  }

   @Test
   @Ignore
   public void manualFile() throws Exception {
    Optional<DatasetWithSettings> m = ColdpMetadataParser.readYAML(new FileInputStream("/Users/markus/code/data/data-col-hierarchy/metadata.yaml"));
    Dataset d = m.get().getDataset();

    var sources = d.getSource();
    System.out.println("Sources");
    sources.forEach(c -> {
      System.out.println(c.getCitation());
      System.out.println(CslUtil.buildCitation(c.toCSL()));
     });
  }
}