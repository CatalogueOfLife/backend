package org.col.importer.coldp;

import java.io.IOException;
import java.nio.file.Path;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import org.col.api.TestEntityGenerator;
import org.col.api.model.Dataset;
import org.col.api.model.Reference;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.License;
import org.col.common.csl.CslUtil;
import org.col.img.ImageService;
import org.col.importer.InserterBaseTest;
import org.col.importer.NeoInserter;
import org.col.importer.reference.ReferenceFactory;
import org.gbif.nameparser.api.NomCode;
import org.junit.Test;

import static org.junit.Assert.*;

public class ColdpInserterTest extends InserterBaseTest {
  
  @Test
  public void readMetadata() throws Exception {
    NeoInserter ins = setup("/coldp/0");
    Dataset d = ins.readMetadata().get();
    
    assertNull(d.getType());
    assertEquals(DataFormat.COLDP, d.getDataFormat());
    assertEquals("The full dataset title", d.getTitle());
    assertNotNull(d.getDescription());
    assertEquals(10, d.getOrganisations().size());
    assertEquals("Nicolas Bailly <nbailly@hcmr.gr>", d.getContact());
    assertEquals(3, d.getAuthorsAndEditors().size());
    assertEquals(License.CC_BY_NC, d.getLicense());
    assertEquals("ver. (06/2018)", d.getVersion());
    assertEquals("2018-06-01", d.getReleased().toString());
    assertEquals("https://www.fishbase.org", d.getWebsite().toString());
    assertEquals("https://www.fishbase.de/images/gifs/fblogo_new.gif", d.getLogo().toString());
    assertEquals("Froese R. & Pauly D. (eds) (2018). FishBase (version 06/2018).", d.getCitation());
  
    assertEquals(NomCode.BOTANICAL, d.getCode());
    assertEquals((Integer)4, d.getConfidence());
    assertEquals((Integer)32, d.getCompleteness());
    assertEquals("my personal,\n" +
                          "very long notes", d.getNotes());
    assertEquals("shortname", d.getAlias());
  }
  
  @Test
  public void bibtex() throws Exception {
    // warm up Nashorn
    CslUtil.buildCitation(TestEntityGenerator.newReference("My Sharona"));
    CslUtil.buildCitation(TestEntityGenerator.newReference("Telecon in Death Valley"));
    // use timer to trace csl citation builder
    MetricRegistry registry = new MetricRegistry();
    CslUtil.register(registry);
    NeoInserter inserter = setup("/coldp/bibtex");
    inserter.insertAll();
  
    Reference r = store.refById("greene");
    assertEquals("title", r.getCsl().getTitle());
    assertEquals(1895, r.getCsl().getIssued().getDateParts()[0][0]);
    assertEquals((Integer) 1895, r.getYear());
  
    r = store.refById("Droege_2016");
    assertEquals("The Global Genome Biodiversity Network (GGBN) Data Standard specification", r.getCsl().getTitle());
    assertEquals(2016, r.getCsl().getIssued().getDateParts()[0][0]);
    assertEquals((Integer) 2016, r.getYear());
  
    // from CSL JSON with different id
    r = store.refById("baw125");
    assertEquals("The Global Genome Biodiversity Network (GGBN) Data Standard specification", r.getCsl().getTitle());
    assertEquals("1758-0463", r.getCsl().getISSN());
    assertEquals(2016, r.getCsl().getIssued().getDateParts()[0][0]);
    assertEquals((Integer) 2016, r.getYear());
  
    r = store.refById("10.1126/science.169.3946.635");
    assertEquals("The Structure of Ordinary Water: New data and interpretations are yielding new insights into this fascinating substance", r.getCsl().getTitle());
    assertEquals("American Association for the Advancement of Science (AAAS)", r.getCsl().getPublisher());
    assertEquals("Science", r.getCsl().getContainerTitle());
    assertEquals("10.1126/science.169.3946.635", r.getCsl().getDOI());
    assertEquals("http://dx.doi.org/10.1126/science.169.3946.635", r.getCsl().getURL());
    assertEquals(1970, r.getCsl().getIssued().getDateParts()[0][0]);
    assertEquals(8, r.getCsl().getIssued().getDateParts()[0][1]);
    assertEquals(14, r.getCsl().getIssued().getDateParts()[0][2]);
    assertEquals((Integer) 1970, r.getYear());
  
    // log timer
    for (Timer t : registry.getTimers().values()) {
      System.out.println("Count: " + t.getCount());
      System.out.println("MeanRate: " + t.getMeanRate());
      System.out.println("OneMinuteRate: " + t.getOneMinuteRate());
      Snapshot snap = t.getSnapshot();
      System.out.println("max: " + snap.getMax());
      System.out.println("min: " + snap.getMin());
      System.out.println("mean: " + snap.getMean());
      System.out.println("median: " + snap.getMedian());
      System.out.println("95th: " + snap.get95thPercentile());
    }
  }
  
  @Override
  public NeoInserter newInserter(Path resource) throws IOException {
    return new ColdpInserter(store, resource, new ReferenceFactory(store), ImageService.passThru());
  }
}
