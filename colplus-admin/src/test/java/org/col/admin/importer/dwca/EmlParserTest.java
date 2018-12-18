package org.col.admin.importer.dwca;

import java.io.IOException;

import org.col.api.model.Dataset;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 *
 */
public class EmlParserTest {
  EmlParser parser = new EmlParser();
  
  private Dataset read(String name) throws IOException {
    return parser.parse(getClass().getResourceAsStream("/metadata/" + name)).get();
  }
  
  @Test
  public void famous() throws Exception {
    Dataset d = read("famous.xml");
    
    assertEquals("Species named after famous people", d.getTitle());
    assertEquals("A li of species named after famous people including musicians and politicians.", d.getDescription());
    assertEquals("https://github.com/mdoering/famous-organism", d.getWebsite().toString());
    //assertEquals("Species named after famous people", d.getLicense());
    assertEquals("Markus Döring (GBIF)", d.getContact());
    assertEquals("[Markus Döring (GBIF)]", d.getAuthorsAndEditors().toString());
    assertEquals("2017-01-19", d.getReleased().toString());
    assertEquals("http://www.marinespecies.org/aphia.php?p=taxdetails&id=146230", d.getLogo().toString());
    assertEquals("cite my famous dataset", d.getCitation());
  }
  
  @Test
  @Ignore("bad expectations")
  public void eml() throws Exception {
    Dataset d = read("eml.xml");
    
    assertEquals("Checklist of the Vascular Plants of Big Lagoon Bog, Big Lagoon County Park, Humboldt County, California", d.getTitle());
    assertNull(d.getDescription());
    assertEquals("https://github.com/mdoering/famous-organism", d.getWebsite().toString());
    //assertEquals("Species named after famous people", d.getLicense());
    assertEquals("Markus Döring (GBIF)", d.getContact());
    assertEquals("[Markus Döring (GBIF)]", d.getAuthorsAndEditors().toString());
    assertEquals("2017-01-19", d.getReleased().toString());
  }
  
  @Test
  @Ignore("bad expectations")
  public void vascan() throws Exception {
    Dataset d = read("vascan.xml");
    
    assertEquals("Database of Vascular Plants of Canada (VASCAN)", d.getTitle());
    assertEquals("A queue of species named after famous people including musicians and politicians.", d.getDescription());
    assertEquals("https://github.com/mdoering/famous-organism", d.getWebsite().toString());
    //assertEquals("Species named after famous people", d.getLicense());
    assertEquals("Markus Döring (GBIF)", d.getContact());
    assertEquals("[Markus Döring (GBIF)]", d.getAuthorsAndEditors().toString());
    assertEquals("2017-01-19", d.getReleased().toString());
  }
  
  @Test
  @Ignore("bad expectations")
  public void worms() throws Exception {
    Dataset d = read("worms_eml2.1.xml");
    
    assertEquals("Species named after famous people", d.getTitle());
    assertEquals("A queue of species named after famous people including musicians and politicians.", d.getDescription());
    assertEquals("https://github.com/mdoering/famous-organism", d.getWebsite().toString());
    //assertEquals("Species named after famous people", d.getLicense());
    assertEquals("Markus Döring (GBIF)", d.getContact());
    assertEquals("[Markus Döring (GBIF)]", d.getAuthorsAndEditors().toString());
    assertEquals("2017-01-19", d.getReleased().toString());
  }
  
}