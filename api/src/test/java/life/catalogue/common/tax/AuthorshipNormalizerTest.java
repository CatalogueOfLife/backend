package life.catalogue.common.tax;

import life.catalogue.api.model.Name;

import org.gbif.nameparser.api.Authorship;

import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import com.google.common.collect.Lists;

import static life.catalogue.common.tax.AuthorshipNormalizer.Author;
import static org.junit.Assert.*;

  public class AuthorshipNormalizerTest {
    AuthorshipNormalizer comp = AuthorshipNormalizer.INSTANCE;

    @Test
    @Ignore("To be implmented still, found in Clearinghouse data logs")
    public void testNormalizeFailures() throws Exception {
      assertEquals("", AuthorshipNormalizer.normalize("epling, nom kudo, 1931"));
      assertEquals("", AuthorshipNormalizer.normalize("rothm ,p p 11233"));
      assertEquals("", AuthorshipNormalizer.normalize(""));
      assertEquals("", AuthorshipNormalizer.normalize(""));
      assertEquals("", AuthorshipNormalizer.normalize(""));
    }

    @Test
    public void testNormalize() throws Exception {
      assertNull(AuthorshipNormalizer.normalize((String) null));
      assertNull(AuthorshipNormalizer.normalize(" "));
      assertNull(AuthorshipNormalizer.normalize("."));
      assertNull(AuthorshipNormalizer.normalize(" (-) "));

      assertEquals("qul", comp.normalize("Quél."));

      assertEquals("doring", AuthorshipNormalizer.normalize("Döring"));
      assertEquals("muller", AuthorshipNormalizer.normalize("Müller"));
      assertEquals("muller", AuthorshipNormalizer.normalize("Mueller"));
      assertEquals("desireno", AuthorshipNormalizer.normalize("Désírèñø"));
      assertEquals("p m e l de la chapelle", AuthorshipNormalizer.normalize("p m é l de la chapelle"));
      
      assertEquals("a j white", AuthorshipNormalizer.normalize("A.J. White"));
      assertEquals("j a white", AuthorshipNormalizer.normalize("J A  WHITE"));
      assertEquals("a j white", AuthorshipNormalizer.normalize("A-J-White"));
      assertEquals("a j white", AuthorshipNormalizer.normalize("(A.J. White)"));
      
      assertEquals("colla", AuthorshipNormalizer.normalize("Colla"));
      assertEquals("schult", AuthorshipNormalizer.normalize("Schult."));
      assertEquals("nevski", AuthorshipNormalizer.normalize("Nevski"));
      assertEquals("w q yin", AuthorshipNormalizer.normalize("W. Q. Yin"));
      
      assertEquals("g kirchn", AuthorshipNormalizer.normalize("G.Kirchn."));
      
      assertEquals("a gray", AuthorshipNormalizer.normalize("A.Gray"));
      assertEquals("c chr", AuthorshipNormalizer.normalize("C. Chr."));
      assertEquals("h christ", AuthorshipNormalizer.normalize("H. Christ"));
      
      assertEquals("l", AuthorshipNormalizer.normalize("L."));
      assertEquals("rchb", AuthorshipNormalizer.normalize("Rchb."));
      assertEquals("rchb", AuthorshipNormalizer.normalize("Rchb."));
      
      assertEquals("muller", AuthorshipNormalizer.normalize("Müller"));
      assertEquals("muller", AuthorshipNormalizer.normalize("Mueller"));
      assertEquals("moller", AuthorshipNormalizer.normalize("Moeller"));
      
      
      assertEquals("l filius", AuthorshipNormalizer.normalize("L. f."));
      assertEquals("l filius", AuthorshipNormalizer.normalize("L.fil."));
      assertEquals("don filius", AuthorshipNormalizer.normalize("Don f."));
      assertEquals("don filius", AuthorshipNormalizer.normalize("Don fil."));
      assertEquals("don filius", AuthorshipNormalizer.normalize("Don fil"));
      assertEquals("f merck", AuthorshipNormalizer.normalize("f. Merck"));
      assertEquals("f merck", AuthorshipNormalizer.normalize("f Merck"));
      assertEquals("la don filius", AuthorshipNormalizer.normalize("la Don f."));
      assertEquals("f rich", AuthorshipNormalizer.normalize("f. Rich."));
      assertEquals("rich filius", AuthorshipNormalizer.normalize("Rich. f."));
      assertEquals("l filius", AuthorshipNormalizer.normalize("L.f."));
      assertEquals("l filius", AuthorshipNormalizer.normalize("L. f."));
      assertEquals("l filius", AuthorshipNormalizer.normalize("L f"));
      assertEquals("lf", AuthorshipNormalizer.normalize("Lf"));
    }
    
    @Test
    public void testLookup() throws Exception {
      assertNull(comp.lookup((String)null));
      assertEquals(" ", comp.lookup(" "));
      assertEquals(".", comp.lookup("."));
      assertEquals("-", comp.lookup("-"));
      
      assertEquals("Döring", comp.lookup("Döring"));
      assertEquals("desireno", comp.lookup("desireno"));
      
      assertEquals("a j white", comp.lookup("a j white"));
      
      assertEquals("l a colla", comp.lookup("colla"));
      assertEquals("j a schultes", comp.lookup("schult"));
      assertEquals("s a nevski", comp.lookup("nevski"));
      assertEquals("w q yin", comp.lookup("w q yin"));
      
      assertEquals("g kirchner", comp.lookup("g kirchn"));
      
      assertEquals("c f a christensen", comp.lookup("c chr"));
      assertEquals("h christ", comp.lookup("h christ"));
      
      assertEquals("c linnaus", comp.lookup("l"));
      assertEquals("h g l reichenbach", comp.lookup("rchb"));
      assertEquals("a p de candolle", comp.lookup("dc"));
      assertEquals("j lamarck", comp.lookup("lam"));
      // the input is a single author. so expect nothing
      assertEquals("lam,dc", comp.lookup("lam,dc"));
      
      assertEquals("c linnaus filius", comp.lookup("l filius"));
      assertEquals("c h bipontinus schultz", comp.lookup("sch bip"));
      assertEquals("c h bipontinus schultz", comp.lookup("schultz bip"));
    }
  
    @Test
    public void normalizeName() throws Exception {
      Name n = new Name();
      assertEqual(comp.normalizeName(n));

      n.setCombinationAuthorship(cr("1999", "Wálthèr", "Döring"));
      assertEqual(comp.normalizeName(n), "doring", "walther");
      
      n.getCombinationAuthorship().setExAuthors(Lists.newArrayList("Käsekuchen"));
      assertEqual(comp.normalizeName(n), "doring", "walther");
  
      n.setBasionymAuthorship(cr("1904", "B.C.Tremendous", "L.", "Linne"));
      n.getBasionymAuthorship().setExAuthors(Lists.newArrayList("BBC Tremendous", "L"));
      assertEqual(comp.normalizeName(n), "linnaus", "tremendous");
    }
  
    private static void assertEqual(List<String> totest, String... expected) {
      assertEquals(Lists.newArrayList(expected), totest);
    }

    private static Authorship cr(String year, String... authors) {
      Authorship a = new Authorship();
      a.setYear(year);
      a.setAuthors(Lists.newArrayList(authors));
      return a;
    }
    
    @Test
    public void testAuthorParsing() throws Exception {
      assertAuthor("l", null, "l");
      assertAuthor("l bolus", "l", "bolus");
      assertAuthor("f k c g s mark", "f k c g s", "mark");
      assertAuthor("p m e l de la chapelle", "p m e l", "chapelle");
      assertAuthor("doring", null, "doring");
      assertAuthor("a j white", "a j", "white");
      assertAuthor("white herbert harvey", null, "harvey");
      assertAuthor("l a colla", "l a", "colla");
      assertAuthor("w q yin", "w q", "yin");
      assertAuthor("g kirchner", "g", "kirchner");
      assertAuthor("h g l reichenbach", "h g l", "reichenbach");
      assertAuthor("c linnaeus filius", "c", "linnaeus");
    }
    
    private void assertAuthor(String full, String initials, String surname) {
      Author a = new Author(full);
      assertEquals(full, a.fullname);
      assertEquals(initials, a.initials);
      assertEquals(surname, a.surname);
    }
    
    @Test
    public void firstInitialsDiffer() throws Exception {
      assertTrue(firstInitialsDiffer("a a mark", "a b mark"));
      assertFalse(firstInitialsDiffer("k f mark", "k f mark"));
      assertFalse(firstInitialsDiffer("k f mark", "f k mark"));
      assertFalse(firstInitialsDiffer("k f mark", "k mark"));
      assertFalse(firstInitialsDiffer("k mark", "k f mark"));
      assertFalse(firstInitialsDiffer("f mark", "k f mark"));
      assertFalse(firstInitialsDiffer("f mark", "f k mark"));
      assertFalse(firstInitialsDiffer("f mark", "f k c g s mark"));
      
      assertTrue(firstInitialsDiffer("k mark", "f mark"));
      assertTrue(firstInitialsDiffer("k f mark", "a f mark"));
      assertTrue(firstInitialsDiffer("a a mark", "a b mark"));
    }
    
    private boolean firstInitialsDiffer(String a1, String a2) {
      return new Author(a1).firstInitialsDiffer(new Author(a2));
    }
    
  }