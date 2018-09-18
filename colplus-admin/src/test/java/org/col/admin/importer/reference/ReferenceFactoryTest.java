package org.col.admin.importer.reference;

import org.col.api.model.Reference;
import org.col.api.model.VerbatimRecord;
import org.mockito.Mock;

import static org.junit.Assert.assertEquals;

public class ReferenceFactoryTest {

  @Mock
  ReferenceStore refStore;

  public void fromACEF() {
    ReferenceFactory rf = new ReferenceFactory(5, refStore);
    VerbatimRecord tr = new VerbatimRecord();
    Reference r = rf.fromACEF("referenceID", "authors", "1920", "title", "details", tr);
    assertEquals("referenceID", r.getId());
    assertEquals("authors", r.getCsl().getAuthor()[0].getLiteral());
    assertEquals("title", r.getCsl().getTitle());
  }

  public void fromDWC() {
    ReferenceFactory rf = new ReferenceFactory(5, refStore);
    VerbatimRecord tr = new VerbatimRecord();
    Reference r = rf.fromDWC("publishedInID", "publishedIn", "publishedInYear", tr);
  }

  public void fromDC() {
    ReferenceFactory rf = new ReferenceFactory(5, refStore);
    VerbatimRecord tr = new VerbatimRecord();
    Reference r =
        rf.fromDC("identifier", "bibliographicCitation", "creator", "date", "title", "source", tr);
  }

  public void from() {
    ReferenceFactory rf = new ReferenceFactory(5, refStore);
    VerbatimRecord tr = new VerbatimRecord();
    Reference r = rf.fromCitation("id", "citation", tr);
  }

}
