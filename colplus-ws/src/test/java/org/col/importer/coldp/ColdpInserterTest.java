package org.col.importer.coldp;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.col.api.model.Dataset;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.DatasetType;
import org.col.api.vocab.License;
import org.gbif.nameparser.api.NomCode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ColdpInserterTest {
  
  @Test
  public void readMetadata() throws Exception {
    URL url = getClass().getResource("/coldp/0");
    Path coldp0 = Paths.get(url.toURI());
    
    ColdpInserter ins = new ColdpInserter(null, coldp0, null);
    Dataset d = ins.readMetadata().get();
    
    assertEquals(DatasetType.OTHER, d.getType());
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
}
