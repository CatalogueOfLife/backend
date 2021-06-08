package life.catalogue.importer.coldp;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Agent;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.Reference;
import life.catalogue.api.vocab.License;
import life.catalogue.common.csl.CslUtil;
import life.catalogue.importer.InserterBaseTest;
import life.catalogue.importer.NeoInserter;
import life.catalogue.importer.reference.ReferenceFactory;

import org.gbif.nameparser.api.NomCode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class ColdpInserterTest extends InserterBaseTest {
  
  @Test
  public void readMetadata() throws Exception {
    NeoInserter ins = setup("/coldp/0");
    DatasetWithSettings d = ins.readMetadata().get();
    
    assertNull(d.getType());
    assertNull(d.getDataFormat());
    assertEquals("The full dataset title", d.getTitle());
    assertNotNull(d.getDescription());
    assertEquals(10, d.getContributor().size());
    assertEquals(new Agent("Rainer", "Froese", "rainer@mailinator.com", null), d.getContact());
    assertEquals(List.of(
        new Agent("Rainer", "Froese", "rainer@mailinator.com", "0000-0001-9745-636X"),
        new Agent("Daniel", "Pauly", null, "0000-0003-3756-4793")
      ), d.getCreator());
    assertNull(d.getEditor());
    assertEquals(License.CC_BY_NC, d.getLicense());
    assertEquals("ver. (06/2018)", d.getVersion());
    assertEquals("2018-06-01", d.getIssued().toString());
    assertEquals("https://www.fishbase.org", d.getUrl().toString());
    assertEquals("https://www.fishbase.de/images/gifs/fblogo_new.gif", d.getLogo().toString());

    assertEquals(NomCode.BOTANICAL, d.getCode());
    assertEquals((Integer)4, d.getConfidence());
    assertEquals((Integer)32, d.getCompleteness());
    assertEquals("my personal,\n" +
                          "very long notes", d.getNotes());
    assertEquals("shortname", d.getAlias());
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
    return new ColdpInserter(store, resource, settings, new ReferenceFactory(store));
  }
}
