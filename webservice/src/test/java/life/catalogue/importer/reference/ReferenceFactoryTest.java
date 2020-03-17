package life.catalogue.importer.reference;

import life.catalogue.api.model.CslName;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.Reference;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.importer.neo.ReferenceStore;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;

public class ReferenceFactoryTest {
  
  final String doi  = "10.1126/science.169.3946.635";
  final String link = "http://dx.doi.org/10.1126/science.169.3946.635";
  
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
    assertEquals("M.Döring. Guess what? (2001).", rf.buildCitation("M.Döring", "2001", "Guess what?", null, null));
  }

  @Test
  public void fromACEF() {
    Reference r = rf.fromACEF("referenceID", "authors", "1920", "title", "details", issues);
    assertEquals("referenceID", r.getId());
    assertEquals(1920, (int) r.getYear());
    assertEquals("authors. title. details. (1920).", r.getCitation());
    assertEquals("authors", r.getCsl().getAuthor()[0].getFamily());
    assertEquals("title", r.getCsl().getTitle());
    assertEquals("details", r.getCsl().getContainerTitle());
  }
  
  @Test
  public void fromColDP() {
    Reference r = rf.fromColDP("referenceID", "my full citation to be ignored", "authors", "1920", "title", "source", "7:details", doi, link, "nonsense", issues);
    assertEquals("referenceID", r.getId());
    assertEquals(1920, (int) r.getYear());
    assertEquals("authors", r.getCsl().getAuthor()[0].getFamily());
    assertEquals("title", r.getCsl().getTitle());
    assertEquals("source", r.getCsl().getContainerTitle());
    assertEquals("nonsense", r.getRemarks());
    assertEquals(doi, r.getCsl().getDOI());
    assertEquals(link, r.getCsl().getURL());
    assertEquals("authors. title. source. 7:details (1920).", r.getCitation());
  }
  
  @Test
  public void authors() {
    // comma
    CslName[] authors = ReferenceFactory.parseAuthors("C.Ulloa Ulloa, P.  Acevedo-Rodríguez, S. G. Beck,  M. J.  de Belgrano, R. Bernal, P. E. Berry, L. Brako, M. dé Celis, G. Davidse, S. R. Gradstein, O. Hokche, B. León, S. de la León-Yánez, R. E. Magill, D. A. Neill, M. H. Nee, P. H. Raven, Stimmel, M. T. Strong, J. L. Villaseñor Ríos, J. L. Zarucchi, F. O. Zuloaga & P. M. Jørgensen", issues);
    assertEquals(23, authors.length);

    CslName a = authors[1];
    assertEquals("P.", a.getGiven());
    assertNull(a.getNonDroppingParticle());
    assertEquals("Acevedo-Rodríguez", a.getFamily());
  
    a = authors[3];
    assertEquals("M. J.", a.getGiven());
    assertEquals("de", a.getNonDroppingParticle());
    assertEquals("Belgrano", a.getFamily());
  
    a = authors[12];
    assertEquals("S.", a.getGiven());
    assertEquals("de la", a.getNonDroppingParticle());
    assertEquals("León-Yánez", a.getFamily());
  
  
    // semicolon
    authors = ReferenceFactory.parseAuthors("Ulloa, C.; Acevedo-Rodríguez, P.; Beck, Sigmund; de la Belgrano, Maria Josef; Simmel", issues);
    assertEquals(5, authors.length);
  
    a = authors[0];
    assertEquals("C.", a.getGiven());
    assertNull(a.getNonDroppingParticle());
    assertEquals("Ulloa", a.getFamily());

    a = authors[1];
    assertEquals("P.", a.getGiven());
    assertNull(a.getNonDroppingParticle());
    assertEquals("Acevedo-Rodríguez", a.getFamily());
  
    a = authors[2];
    assertEquals("Sigmund", a.getGiven());
    assertNull(a.getNonDroppingParticle());
    assertEquals("Beck", a.getFamily());
  
    a = authors[3];
    assertEquals("Maria Josef", a.getGiven());
    assertEquals("de la", a.getNonDroppingParticle());
    assertEquals("Belgrano", a.getFamily());
  
    a = authors[4];
    assertNull(a.getGiven());
    assertNull(a.getNonDroppingParticle());
    assertEquals("Simmel", a.getFamily());
  
  
    authors = ReferenceFactory.parseAuthors("Sautya, S., Tabachnick, K.R., Ingole, B.", issues);
    assertEquals(3, authors.length);
  
    a = authors[0];
    assertEquals("S.", a.getGiven());
    assertNull(a.getNonDroppingParticle());
    assertEquals("Sautya", a.getFamily());
  
    a = authors[1];
    assertEquals("K.R.", a.getGiven());
    assertNull(a.getNonDroppingParticle());
    assertEquals("Tabachnick", a.getFamily());
  
    a = authors[2];
    assertEquals("B.", a.getGiven());
    assertNull(a.getNonDroppingParticle());
    assertEquals("Ingole", a.getFamily());
  
  
    authors = ReferenceFactory.parseAuthors("Sautya,S., Tabachnick,K.R.,Ingole,B.", issues);
    assertEquals(3, authors.length);
  
    a = authors[0];
    assertEquals("S.", a.getGiven());
    assertNull(a.getNonDroppingParticle());
    assertEquals("Sautya", a.getFamily());
  
    a = authors[1];
    assertEquals("K.R.", a.getGiven());
    assertNull(a.getNonDroppingParticle());
    assertEquals("Tabachnick", a.getFamily());
  
    a = authors[2];
    assertEquals("B.", a.getGiven());
    assertNull(a.getNonDroppingParticle());
    assertEquals("Ingole", a.getFamily());
  }
  
  @Test
  public void fromDWC() {
    Reference r = rf.fromDWC("12345", "Dingle Doodle da", "1888", issues);
    assertEquals("12345", r.getId());
    assertEquals("Dingle Doodle da. (1888).", r.getCitation());
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
    assertEquals("Dembridge", r.getCsl().getAuthor()[0].getFamily());
    assertEquals("M.", r.getCsl().getAuthor()[0].getGiven());
    assertEquals(2008, r.getCsl().getIssued().getDateParts()[0][0]);
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
