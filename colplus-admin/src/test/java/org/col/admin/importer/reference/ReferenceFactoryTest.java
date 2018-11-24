package org.col.admin.importer.reference;

import org.col.api.model.IssueContainer;
import org.col.api.model.Reference;
import org.col.api.model.VerbatimRecord;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ReferenceFactoryTest {
  
  @Mock
  ReferenceStore refStore;
  ReferenceFactory rf;
  IssueContainer issues;
  
  @Before
  public void init(){
    MockitoAnnotations.initMocks(this);
    rf = new ReferenceFactory(5, refStore);
    issues = new VerbatimRecord();
  }
  
  @Test
  public void citation() {
    assertEquals("M.Döring. Guess what? 2001.", rf.buildCitation("M.Döring", "2001", "Guess what?", null));
  }
  
  @Test
  public void fromACEF() {
    Reference r = rf.fromACEF("referenceID", "authors", "1920", "title", "details", issues);
    assertEquals("referenceID", r.getId());
    assertEquals(1920, (int) r.getYear());
    assertEquals("authors. title. details. 1920.", r.getCitation());
    assertEquals("authors", r.getCsl().getAuthor()[0].getLiteral());
    assertEquals("title", r.getCsl().getTitle());
    assertEquals("details", r.getCsl().getContainerTitle());
  }
  
  @Test
  public void fromDWC() {
    Reference r = rf.fromDWC("12345", "Dingle Doodle da", "1888", issues);
    assertEquals("12345", r.getId());
    assertEquals("Dingle Doodle da. 1888.", r.getCitation());
    assertEquals(1888, (int) r.getYear());
    assertNull(r.getCsl());
  }
  
  @Test
  public void fromDC() {
    Reference r = rf.fromDC("doi:10.4657/e463dgv", "full citation missing the year", "Dembridge, M.", "May 2008", "My great garden", "Journal of Herbs", issues);
    assertEquals("doi:10.4657/e463dgv", r.getId());
    assertEquals("full citation missing the year", r.getCitation());
    assertEquals(2008, (int) r.getYear());
    assertNotNull(r.getCsl());
    assertEquals("My great garden", r.getCsl().getTitle());
    assertEquals("Dembridge, M.", r.getCsl().getAuthor()[0].getLiteral());
    assertNotNull(r.getCsl().getIssued());
    assertEquals("Journal of Herbs", r.getCsl().getContainerTitle());
  }
  
  @Test
  public void fromCitation() {
    Reference r = rf.fromCitation("id", "citation", issues);
    assertEquals("id", r.getId());
    assertEquals("citation", r.getCitation());
    assertNull(r.getYear());
    assertNull(r.getCsl());
  }
  
}
