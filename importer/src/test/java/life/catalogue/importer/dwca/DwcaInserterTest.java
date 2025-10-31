package life.catalogue.importer.dwca;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Environment;
import life.catalogue.api.vocab.Gazetteer;
import life.catalogue.api.vocab.License;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.importer.InserterBaseTest;
import life.catalogue.importer.NeoInserter;
import life.catalogue.importer.neo.model.NeoUsage;

import org.gbif.dwc.terms.UnknownTerm;
import org.gbif.nameparser.api.NomCode;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.graphdb.Transaction;

import static org.junit.Assert.*;

public class DwcaInserterTest extends InserterBaseTest {
  
  @Override
  public NeoInserter newInserter(Path resource, DatasetSettings settings) throws IOException  {
    return new DwcaInserter(store, resource, settings, refFactory);
  }

  /**
   * WoRMS species profile with extinct
   */
  @Test
  public void speciesProfiles() throws Exception {
    NeoInserter ins = setup("/dwca/50");
    ins.insertAll();

    try (Transaction tx = store.getNeo().beginTx()) {
      var t = store.usageWithName("1", tx).asTaxon();
      assertTrue(t.isExtinct());
      assertEquals(Set.of(Environment.MARINE, Environment.TERRESTRIAL), t.getEnvironments());

      t = store.usageWithName("2", tx).asTaxon();
      assertFalse(t.isExtinct());
      assertEquals(Set.of(Environment.MARINE), t.getEnvironments());

      t = store.usageWithName("3", tx).asTaxon();
      assertFalse(t.isExtinct());
      assertEquals(Set.of(), t.getEnvironments());

      t = store.usageWithName("4", tx).asTaxon();
      assertTrue(t.isExtinct());
      assertEquals(Set.of(), t.getEnvironments());

      t = store.usageWithName("5", tx).asTaxon();
      assertTrue(t.isExtinct());
      assertEquals(Set.of(Environment.MARINE), t.getEnvironments());

      t = store.usageWithName("6", tx).asTaxon();
      assertNull(t.isExtinct());
      assertEquals(Set.of(), t.getEnvironments());
    }
  }

  /**
   * GBIF Identifier extension
   */
  @Test
  public void altIds() throws Exception {
    NeoInserter ins = setup("/dwca/53");
    ins.insertAll();

    try (Transaction tx = store.getNeo().beginTx()) {
      var t = store.usageWithName("763571", tx).asTaxon();
      assertEquals(5, t.getIdentifier().size());
    }
  }

  /**
   * Plazi with COL metadata.json
   */
  @Test
  public void dwca40() throws Exception {
    NeoInserter ins = setup("/dwca/40");
    ins.insertAll();

    var m = ins.readMetadata().get();
    // should read json, not eml!
    assertEquals("Chapter 7: Linnaean Plant Names and their Types (part Q)", m.getTitle());
    assertEquals("93F443DCBCEDFBF26165C392E1E8901C", m.getIdentifier().get("plazi"));
    assertEquals("Department of Botany, Natural History Museum, Cromwell Road, London, UK", m.getCreator().get(0).getOrganisation());
    assertEquals(License.CC0, m.getLicense());
    assertEquals(1, m.getSource().size());
    assertEquals("Linnaean Society of London in association with the Natural History Museum", m.getSource().get(0).getPublisher());
  }

  @Test
  public void dwca42() throws Exception {
    NeoInserter ins = setup("/dwca/42");
    ins.insertAll();

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage u = store.usageWithName("530938-wcs", tx);
      var nn = u.getNeoName();

      var ref = store.references().get(nn.getName().getPublishedInId());
      assertEquals("Bot. J. Linn. Soc. (2018)", ref.getCitation());
      assertEquals((Integer) 2018, ref.getYear());
    }
  }

  /**
   * EEA redlist file with unknown term columns
   */
  @Test
  public void dwca37() throws Exception {
    NeoInserter ins = setup("/dwca/37");
    ins.insertAll();

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage u = store.usages().objByID("319088", tx);
      assertNotNull(u.getVerbatimKey());
      VerbatimRecord v = store.getVerbatim(u.getVerbatimKey());
      v.hasTerm(new UnknownTerm(URI.create("http://unknown.org/CoL_name"), false));
    }
  }

  @Test
  @Ignore("unfinished")
  public void plazi2() throws Exception {
    NeoInserter ins = setup("/dwca/plazi2");
    ins.insertAll();

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage u = store.usageWithName("03E387995E15FFE0FF36F93FFD2935BE.taxon", tx);
      assertEquals("Isoperla eximia :Zapekina-Dulkeit 1975", u.getNeoName().getName().getScientificName());
      assertEquals(": Zapekina-Dulkeit, 1975", u.getNeoName().getName().getAuthorship());
      assertEquals("Isoperla eximia : Zapekina-Dulkeit 1975", u.getNeoName().getName().getLabel());
    }
  }

  @Test
  public void readMetadata() throws Exception {
    NeoInserter ins = setup("/dwca/38");
    DatasetWithSettings d = ins.readMetadata().get();

    Agent markus = Agent.person("Markus", "Döring", "mdoering@gbif.org", "0000-0001-7757-1889");
    markus.setOrganisation("GBIF");

    assertEquals("Species named after famous people", d.getTitle());
    assertEquals("A list of species named after famous people including musicians and politicians.", d.getDescription());
    assertEquals("https://github.com/mdoering/famous-organism", d.getUrl().toString());
    assertEquals(markus, d.getContact());
    assertEquals(List.of(markus), d.getCreator());
    assertEquals("2017-01-19", d.getIssued().toString());
    assertEquals("http://www.marinespecies.org/aphia.php?p=taxdetails&id=146230", d.getLogo().toString());
    assertEquals("Famous People", d.getAlias());
  }

  @Test
  public void readYamlMetadata() throws Exception {
    NeoInserter ins = setup("/dwca/39");
    DatasetWithSettings d = ins.readMetadata().get();

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


}