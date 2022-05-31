package life.catalogue.importer.coldp;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.License;
import life.catalogue.common.csl.CslDataConverter;
import life.catalogue.common.csl.CslUtil;
import life.catalogue.importer.InserterBaseTest;
import life.catalogue.importer.NeoInserter;
import life.catalogue.importer.neo.model.RelType;

import org.gbif.nameparser.api.Rank;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import org.jbibtex.BibTeXDatabase;
import org.junit.Test;
import org.neo4j.graphdb.Transaction;

import de.undercouch.citeproc.bibtex.BibTeXConverter;
import de.undercouch.citeproc.csl.CSLType;

import static org.junit.Assert.*;

public class ColdpInserterTest extends InserterBaseTest {

  @Test
  public void bareNamesWithAccordingToID() throws Exception {
    NeoInserter ins = setup("/coldp/20");
    ins.insertAll();

    try (Transaction tx = store.getNeo().beginTx()) {
      assertNull(store.usages().objByID("111"));
      assertNull(store.usages().objByID("222"));

      var n = store.names().objByID("111").getName();
      assertEquals("Catyclia", n.getScientificName());
      assertEquals(Rank.GENUS, n.getRank());

      n = store.names().objByID("222").getName();
      assertEquals("Killmora", n.getScientificName());
      assertEquals(Rank.GENUS, n.getRank());
    }
  }

  @Test
  public void readMetadata() throws Exception {
    NeoInserter ins = setup("/coldp/0");
    DatasetWithSettings d = ins.readMetadata().get();
    
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
  }

  @Test
  public void relatedName() throws Exception {
    NeoInserter ins = setup("/coldp/16");
    ins.insertAll();

    for (RelType rt : RelType.values()) {
      if (rt.isSpeciesInteraction()) {
        try (Transaction tx = store.getNeo().beginTx()) {
          store.iterRelations(rt).stream().forEach(rel -> {
            var si = store.toSpeciesInteraction(rel);
            assertNotNull(si.getTaxonId());
            assertNotNull(si.getRelatedTaxonScientificName());
            assertEquals(rt.specInterType, si.getType());
          });
        }
      }
    }
  }

  @Test
  public void fungi13() throws Exception {
    NeoInserter ins = setup("/coldp/13");
    ins.insertAll();

    try (Transaction tx = store.getNeo().beginTx()) {
      Name n = store.names().objByID("139502").getName();
      assertEquals("Agaricus candidus caerulescens", n.getScientificName());
      assertEquals("Agaricus", n.getGenus());
      assertEquals("candidus", n.getSpecificEpithet());
      assertEquals("caerulescens", n.getInfraspecificEpithet());
      assertNull(n.getInfragenericEpithet());
      assertNull(n.getUninomial());
      assertEquals(Rank.OTHER, n.getRank());

      n = store.names().objByID("588900").getName();
      assertEquals("Lecidea sabuletorum sabuletorum", n.getScientificName());
      assertEquals("Lecidea", n.getGenus());
      assertEquals("sabuletorum", n.getSpecificEpithet());
      assertEquals("sabuletorum", n.getInfraspecificEpithet());
      assertNull(n.getInfragenericEpithet());
      assertNull(n.getUninomial());
      assertEquals(Rank.OTHER, n.getRank());
    }
  }


  @Test
  public void bibtexConverter() throws Exception {
    String bibtex = "@BOOK{test,\n"
                    + "author = {Peter Lustig and others},\n"
                    + "title = {Ueber das Schreiben von Blindtexten},\n"
                    + "year = {2007},\n"
                    + "address = {Berlin}\n"
                    + "}";
    InputStream is = new ByteArrayInputStream(bibtex.getBytes());
    BibTeXConverter bc = new BibTeXConverter();
    BibTeXDatabase db = bc.loadDatabase(is);
    bc.toItemData(db).forEach((id, cslItem) -> {
      CslData csl = CslDataConverter.toCslData(cslItem);
      assertEquals("test", csl.getId());
      assertEquals(CSLType.BOOK, csl.getType());
      assertEquals("Ueber das Schreiben von Blindtexten", csl.getTitle());
      assertEquals(new CslDate(2007), csl.getIssued());
      assertEquals("Berlin", csl.getPublisherPlace());
      assertEquals(2, csl.getAuthor().length);
      assertEquals(new CslName("Peter", "Lustig"), csl.getAuthor()[0]);
      assertEquals(new CslName("others"), csl.getAuthor()[1]);
      System.out.println( CslUtil.buildCitation(csl) );
    });
  }

  @Test
  public void bibtex() throws Exception {
    // warm up CSL formatter
    CslUtil.buildCitation(TestEntityGenerator.newReference("My Sharona"));
    CslUtil.buildCitation(TestEntityGenerator.newReference("Telecon in Death Valley"));

    NeoInserter inserter = setup("/coldp/bibtex");
    inserter.insertAll();
  
    Reference r = store.references().get("greene");
    assertEquals("title", r.getCsl().getTitle());
    assertEquals(1895, r.getCsl().getIssued().getDateParts()[0][0]);
    assertEquals((Integer) 1895, r.getYear());
  
    r = store.references().get("Droege_2016");
    assertEquals("The Global Genome Biodiversity Network (GGBN) Data Standard specification", r.getCsl().getTitle());
    assertEquals(2016, r.getCsl().getIssued().getDateParts()[0][0]);
    assertEquals((Integer) 2016, r.getYear());
  
    // from CSL JSON with different id
    r = store.references().get("baw125");
    assertEquals("The Global Genome Biodiversity Network (GGBN) Data Standard specification", r.getCsl().getTitle());
    assertEquals("1758-0463", r.getCsl().getISSN());
    assertEquals(2016, r.getCsl().getIssued().getDateParts()[0][0]);
    assertEquals((Integer) 2016, r.getYear());
  
    r = store.references().get("10.1126/science.169.3946.635");
    assertEquals("The Structure of Ordinary Water: New data and interpretations are yielding new insights into this fascinating substance", r.getCsl().getTitle());
    assertEquals("American Association for the Advancement of Science (AAAS)", r.getCsl().getPublisher());
    assertEquals("Science", r.getCsl().getContainerTitle());
    assertEquals("10.1126/science.169.3946.635", r.getCsl().getDOI());
    assertEquals("http://dx.doi.org/10.1126/science.169.3946.635", r.getCsl().getURL());
    assertEquals(1970, r.getCsl().getIssued().getDateParts()[0][0]);
    assertEquals(8, r.getCsl().getIssued().getDateParts()[0][1]);
    assertEquals(14, r.getCsl().getIssued().getDateParts()[0][2]);
    assertEquals((Integer) 1970, r.getYear());
  }
  
  @Override
  public NeoInserter newInserter(Path resource, DatasetSettings settings) throws IOException {
    return new ColdpInserter(store, resource, settings, refFactory);
  }
}
