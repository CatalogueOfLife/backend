package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DoiResolution;
import life.catalogue.api.vocab.terms.BiboOntTerm;
import life.catalogue.api.vocab.terms.EolReferenceTerm;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.csl.CslUtil;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.metadata.DoiResolver;

import java.util.List;

import org.gbif.dwc.terms.DcTerm;

import org.gbif.dwc.terms.DwcTerm;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import de.undercouch.citeproc.csl.CSLType;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class ReferenceFactoryTest {

  final String doi  = "10.1126/science.169.3946.635";
  final String link = "http://dx.doi.org/10.1126/science.169.3946.635";

  ReferenceStore refStore = ReferenceStore.passThru();
  DoiResolver resolver;
  ReferenceFactory rf;
  VerbatimRecord issues;
  Citation citation;

  @Before
  public void init(){
    MockitoAnnotations.initMocks(this);
    issues = new VerbatimRecord();
    resolver = Mockito.mock(DoiResolver.class);
    DOI d = new DOI(doi);

    citation = new Citation();
    citation.setType(CSLType.BOOK);
    citation.setTitle("A great book to read");
    citation.setAuthor(List.of(new CslName("Charles")));
    citation.setIssued(FuzzyDate.of(1878));
    citation.setDoi(d);
    when(resolver.resolve(any(DOI.class), any(VerbatimRecord.class))).thenReturn(citation);

    rf = new ReferenceFactory(5, refStore, resolver);
    rf.setResolveDOIs(DoiResolution.NEVER);
  }

  @Test
  public void doiResolution() {
    rf.setResolveDOIs(DoiResolution.ALWAYS);

    VerbatimRecord v = new VerbatimRecord();
    v.put(ColdpTerm.ID, "100");
    v.put(ColdpTerm.citation, "Charles (1878): A great book to read");
    var r = rf.fromColDP(v);
    assertEquals(v.get(ColdpTerm.citation), r.getCitation());
    assertNull(r.getCsl().getTitle());
    assertNull(r.getCsl().getDOI());

    v.put(ColdpTerm.doi, doi);
    r = rf.fromColDP(v);
    assertEquals(doi, r.getCsl().getDOI());
    assertEquals(citation.getCitationText(), r.getCitation());
    assertEquals(citation.getTitle(), r.getCsl().getTitle());
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

    r = rf.fromACEF("1234", "Greuter,W. et al. (Eds.)", "1989", null, "Med-Checklist Vol.4 (published)", issues);
    assertEquals("1234", r.getId());
    assertEquals(1989, (int) r.getYear());
    assertNull(r.getCsl().getAuthor());
    assertEquals(1, r.getCsl().getEditor().length);
    assertEquals(new CslName("Greuter,W. et al."), r.getCsl().getEditor()[0]);
    assertEquals("Med-Checklist Vol.4 (published)", r.getCsl().getContainerTitle());
    assertEquals("Greuter,W. et al. (1989). Med-Checklist Vol.4 (Published).", r.getCitation());
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
  public void fromEOL() {
    VerbatimRecord v = new VerbatimRecord();
    v.getTerms().put(DcTerm.identifier, "0CF2621593C45BDA86AE45DE3C908ADD.ref");
    v.getTerms().put(DwcTerm.taxonID, "0CF2621593C45BDA86AE45DE3C908ADD.taxon");
    v.getTerms().put(EolReferenceTerm.publicationType, "journal article");
    v.getTerms().put(EolReferenceTerm.full_reference, "Zhang, Zhen, Zhang, Mei-Jiao, Zhang, Jian-Hang, Zhang, De-Shun, Li, Hong-Qing (2022): Ficus motuoensis (Moraceae), a new species from southwest China. PhytoKeys 206, 119-127: 119-119, DOI:http://dx.doi.org/10.3897/phytokeys.206.89338, URL:http://dx.doi.org/10.3897/phytokeys.206.89338");
    v.getTerms().put(EolReferenceTerm.primaryTitle, "Ficus motuoensis (Moraceae), a new species from southwest China");
    v.getTerms().put(BiboOntTerm.pages, "119");
    v.getTerms().put(BiboOntTerm.pageStart, "119");
    v.getTerms().put(BiboOntTerm.journal, "PhytoKeys");
    v.getTerms().put(BiboOntTerm.volume, "206");
    v.getTerms().put(BiboOntTerm.authorList, "Zhang, Zhen;Zhang, Mei-Jiao;Zhang, Jian-Hang;Zhang, De-Shun;Li, Hong-Qing");
    v.getTerms().put(DcTerm.created, "2022");
    v.getTerms().put(DcTerm.language, "en");

    Reference r = rf.fromEOL(v);
    assertEquals(v.get(DcTerm.identifier), r.getId());
    assertEquals(2022, (int) r.getYear());
    assertEquals("Zhang", r.getCsl().getAuthor()[0].getFamily());
    assertEquals(v.get(EolReferenceTerm.primaryTitle), r.getCsl().getTitle());
    assertEquals("PhytoKeys", r.getCsl().getContainerTitle());
    assertEquals("206", r.getCsl().getVolume());
    assertEquals("119", r.getCsl().getPage());
    assertNull(r.getRemarks());
    assertNull(r.getCsl().getDOI());
    assertNull(r.getCsl().getURL());
    assertEquals("Zhang, Z., Zhang, M.-J., Zhang, J.-H., Zhang, D.-S., & Li, H.-Q. (2022). Ficus motuoensis (Moraceae), a new species from southwest China. PhytoKeys, 206, 119.", r.getCitation());
  }

  @Test
  public void authors() {
    // comma, initials front
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

    // comma initials behind
    authors = ReferenceFactory.parseAuthors("Bakis Y., Babac M.T. & Uslu E.", issues);
    assertEquals(3, authors.length);
    assertEquals("Bakis", authors[0].getFamily());
    assertEquals("Y.", authors[0].getGiven());
    assertEquals("Babac", authors[1].getFamily());
    assertEquals("M.T.", authors[1].getGiven());
    assertEquals("Uslu", authors[2].getFamily());
    assertEquals("E.", authors[2].getGiven());

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


    // comma within authors and between
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
      new CslName("Harry Mulisch the Greatest"),
      new CslName("Sigmund", "Beck"),
      new CslName("P.", "Acevedo-Rodríguez"),
      new CslName("Maria Josef", "Belgrano", "de la")
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
    assertEquals("Dingle Doodle da 1888", r.getCitation());
    assertEquals(1888, (int) r.getYear());
    assertNull(r.getCsl());

    r = rf.fromDWC("12345", "Proc. Biol. Soc. Washington", "(1888)", issues);
    assertEquals("Proc. Biol. Soc. Washington (1888)", r.getCitation());
    assertEquals(1888, (int) r.getYear());
    assertNull(r.getCsl());
  }

  @Test
  public void fromDC() {
    // first without important parts, we should use the full citation here
    Reference r = rf.fromDC("doi:10.4657/e463dgv", "full citation missing the year", null, "May 2008", null, null, issues);
    assertEquals("doi:10.4657/e463dgv", r.getId());
    assertEquals("full citation missing the year", r.getCitation());
    assertEquals(2008, (int) r.getYear());
    assertNotNull(r.getCsl());
    assertNull(r.getCsl().getTitle());
    assertNull(r.getCsl().getAuthor());
    assertEquals(2008, r.getCsl().getIssued().getDateParts()[0][0]);
    assertNull(r.getCsl().getContainerTitle());

    // now with parsed bits, generating a new citation
    r = rf.fromDC("doi:10.4657/e463dgv", "full citation missing the year", "Dembridge, M.", "May 2008", "My great garden", "Journal of Herbs", issues);
    assertEquals("doi:10.4657/e463dgv", r.getId());
    assertEquals("Dembridge, M. (2008). My great garden. Journal of Herbs. https://doi.org/10.4657/e463dgv", r.getCitation());
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
