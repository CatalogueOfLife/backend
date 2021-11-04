package life.catalogue.importer.reference;

import life.catalogue.api.model.CslName;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.Reference;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.csl.CslUtil;
import life.catalogue.importer.neo.ReferenceMapStore;

import org.checkerframework.checker.units.qual.C;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;

public class ReferenceFactoryTest {
  
  final String doi  = "10.1126/science.169.3946.635";
  final String link = "http://dx.doi.org/10.1126/science.169.3946.635";
  
  @Mock
  ReferenceMapStore refStore;
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
    assertEquals("M.Döring. Guess what? (2001).", rf.buildCitation("M.Döring", "2001", "Guess what?", null));
  }

  @Test
  public void fromACEF() {
    Reference r = rf.fromACEF("referenceID", "authors", "1920", "title", "details", issues);
    assertEquals("referenceID", r.getId());
    assertEquals(1920, (int) r.getYear());
    assertEquals("authors. (1920). title. Details.", r.getCitation());
    assertEquals("authors", r.getCsl().getAuthor()[0].getFamily());
    assertEquals("title", r.getCsl().getTitle());
    assertEquals("details", r.getCsl().getContainerTitle());
  }
  
  @Test
  public void fromColDP() {
    VerbatimRecord v = new VerbatimRecord();
    v.getTerms().put(ColdpTerm.ID, "referenceID");
    v.getTerms().put(ColdpTerm.citation, "my full citation to be ignored");
    v.getTerms().put(ColdpTerm.author, "authors");
    v.getTerms().put(ColdpTerm.title, "title");
    v.getTerms().put(ColdpTerm.containerTitle, "source");
    v.getTerms().put(ColdpTerm.issued, "1920");
    v.getTerms().put(ColdpTerm.volume, "7");
    v.getTerms().put(ColdpTerm.issue, "31");
    v.getTerms().put(ColdpTerm.doi, doi);
    v.getTerms().put(ColdpTerm.link, link);
    v.getTerms().put(ColdpTerm.remarks, "nonsense");

    Reference r = rf.fromColDP(v);
    assertEquals("referenceID", r.getId());
    assertEquals(1920, (int) r.getYear());
    assertEquals("authors", r.getCsl().getAuthor()[0].getFamily());
    assertEquals("title", r.getCsl().getTitle());
    assertEquals("source", r.getCsl().getContainerTitle());
    assertEquals("nonsense", r.getRemarks());
    assertEquals(doi, r.getCsl().getDOI());
    assertEquals(link, r.getCsl().getURL());
    assertEquals("authors. (1920). title. Source, 7(31). https://doi.org/10.1126/science.169.3946.635", r.getCitation());
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
  public void authorsRoundtrip() {
    CslName[] names = new CslName[]{
      new CslName(null, "Harry Mulisch the Greatest"),
      new CslName("Sigmund", "Beck"),
      new CslName("P.", "Acevedo-Rodríguez"),
      new CslName("Maria Josef", "Belgrano", "de la", null)
    };

    String x = CslUtil.toColdpString(names);
    System.out.println(x);

    IssueContainer issues = IssueContainer.simple();
    CslName[] names2 = ReferenceFactory.parseAuthors(x, issues);

    assertFalse(issues.hasIssues());
    assertEquals(names, names2);
  }

  @Test
  public void fromDWC() {
    Reference r = rf.fromDWC("12345", "Dingle Doodle da", "1888", issues);
    assertEquals("12345", r.getId());
    assertEquals("Dingle Doodle da (1888)", r.getCitation());
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
