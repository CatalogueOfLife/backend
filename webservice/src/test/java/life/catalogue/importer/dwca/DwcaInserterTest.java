package life.catalogue.importer.dwca;

import com.google.common.collect.Lists;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.Person;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.Gazetteer;
import life.catalogue.api.vocab.License;
import life.catalogue.importer.InserterBaseTest;
import life.catalogue.importer.NeoInserter;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.reference.ReferenceFactory;

import org.gbif.nameparser.api.NomCode;

import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.graphdb.Transaction;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class DwcaInserterTest extends InserterBaseTest {
  
  @Override
  public NeoInserter newInserter(Path resource, DatasetSettings settings) throws IOException  {
    return new DwcaInserter(store, resource, settings, new ReferenceFactory(store));
  }
  /**
   * EEA redlist file with unknown term columns
   */
  @Test
  public void dwca37() throws Exception {
    NeoInserter ins = setup("/dwca/37");
    ins.insertAll();

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage u = store.usages().objByID("319088");
      assertNotNull(u.getVerbatimKey());
      VerbatimRecord v = store.getVerbatim(u.getVerbatimKey());
      v.hasTerm(DwcaReaderTest.TERM_CoL_name);
    }
  }

  @Test
  public void readMetadata() throws Exception {
    NeoInserter ins = setup("/dwca/38");
    DatasetWithSettings d = ins.readMetadata().get();

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
  @Ignore
  public void readYamlMetadata() throws Exception {
    NeoInserter ins = setup("/dwca/39");
    DatasetWithSettings d = ins.readMetadata().get();

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

    assertNull(d.getOrganisations());

    List<Person> authors = new ArrayList<>();
    authors.add(donald);
    authors.add(new Person("Cees", "Gielis", null, "0000-0003-0857-1679"));
    assertEquals(authors, d.getAuthors());
    assertNull(d.getEditors());
  }


}