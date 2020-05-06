package life.catalogue.importer.coldp;

import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.vocab.Gazetteer;
import life.catalogue.api.vocab.License;
import life.catalogue.common.io.Resources;
import org.gbif.nameparser.api.NomCode;
import org.junit.Test;

import java.net.URI;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class MetadataParserTest {
  
  @Test
  public void cycad(){
    MetadataParser mp = new MetadataParser();
    Optional<DatasetWithSettings> m = mp.readMetadata(Resources.stream("metadata/cycads.yaml"));
    DatasetWithSettings d = m.get();
    assertEquals("The World List of Cycads, online edition", d.getTitle());
    assertEquals("Cycad List", d.getAlias());
    assertEquals("The World List of Cycads is a working list of known cycad species names with the primary goal of providing reliable information on the taxonomy of cycads for use by researchers, conservation planners, and others. It is developed in close collaboration with world's foremost cycad experts and published under the auspices of the IUCN's Cycad Specialist Group. The printed edition is published in the proceedings of the International Conference of Cycad Biology, which is held every three years.", d.getDescription());
    assertEquals("Michael Calonje <michaelc@montgomerybotanical.org>", d.getContact());
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

    List<String> orgs = Arrays.asList("IUCN / SSC Cycad Specialist Group, Montgomery Botanical Center, Coral Gables, FL, USA",
        "New York Botanical Garden, Bronx NY, USA",
        "Royal Botanic Gardens, Sydney, New South Wales, Australia");
    assertEquals(orgs, d.getOrganisations());
  
    List<String> authors = Arrays.asList("Michael Calonje orcid:0000-0001-9650-3136", "Leonie Stanberg", "Dennis Stevenson orcid:0000-0002-2986-7076");
    assertEquals(authors, d.getAuthorsAndEditors());
  
  }
}