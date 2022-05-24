package life.catalogue.matching.authorship;

import life.catalogue.api.model.Name;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.Equality;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.ParsedAuthorship;

import java.util.List;

import org.junit.Test;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class AuthorComparatorTest {
  AuthorComparator comp = new AuthorComparator(AuthorshipNormalizer.INSTANCE);
  
  public static Authorship parse(String x) throws InterruptedException {
    return NameParser.PARSER.parseAuthorship(x).orElse(new ParsedAuthorship()).getCombinationAuthorship();
  }
  
  public static Authorship parse(String x, String year) throws InterruptedException {
    if (year != null) {
      x = Strings.nullToEmpty(x) + " " + year;
    }
    return parse(x);
  }
  
  @Test
  public void testCompareName() throws Exception {
    Name p1 = new Name();
    Name p2 = new Name();
    
    assertEquals(Equality.UNKNOWN, comp.compare(p1, p2));
    
    p1.getCombinationAuthorship().setAuthors(Lists.newArrayList("L."));
    assertEquals(Equality.UNKNOWN, comp.compare(p1, p2));
    
    p2.getCombinationAuthorship().setAuthors(Lists.newArrayList("Linne"));
    ;
    assertEquals(Equality.EQUAL, comp.compare(p1, p2));
    
    p1.getCombinationAuthorship().setAuthors(Lists.newArrayList("Linné"));
    ;
    assertEquals(Equality.EQUAL, comp.compare(p1, p2));
    
    p1.getCombinationAuthorship().setYear("1847");
    p2.getCombinationAuthorship().setYear("1877");
    assertEquals(Equality.DIFFERENT, comp.compare(p1, p2));
    
    p2.getCombinationAuthorship().setAuthors(Lists.newArrayList("Carl von Linne"));
    ;
    assertEquals(Equality.DIFFERENT, comp.compare(p1, p2));
    
    p2.getCombinationAuthorship().setYear("184?");
    assertEquals(Equality.EQUAL, comp.compare(p1, p2));
    
    
    p1 = new Name();
    p1.getCombinationAuthorship().setAuthors(Lists.newArrayList("Reich."));
    ;
    
    p2 = new Name();
    p2.getCombinationAuthorship().getAuthors().add("");
    assertEquals(Equality.UNKNOWN, comp.compare(p1, p2));
    
    p2.getCombinationAuthorship().setAuthors(Lists.newArrayList("Reichen."));
    ;
    assertEquals(Equality.EQUAL, comp.compare(p1, p2));
    
    p2.getCombinationAuthorship().setAuthors(Lists.newArrayList("Reichenbrg."));
    ;
    assertEquals(Equality.EQUAL, comp.compare(p1, p2));
    
    p2.getCombinationAuthorship().setAuthors(Lists.newArrayList("Reichenberger"));
    ;
    assertEquals(Equality.EQUAL, comp.compare(p1, p2));
    
    p2.getCombinationAuthorship().setAuthors(Lists.newArrayList("Müller"));
    ;
    assertEquals(Equality.DIFFERENT, comp.compare(p1, p2));
    
    p2.setCombinationAuthorship(parse("Jenkins, Marx & Kluse"));
    ;
    assertEquals(Equality.DIFFERENT, comp.compare(p1, p2));
    
    p1.getCombinationAuthorship().setAuthors(Lists.newArrayList("Mill."));
    ;
    p2.getCombinationAuthorship().setAuthors(Lists.newArrayList("L."));
    ;
    assertEquals(Equality.DIFFERENT, comp.compare(p1, p2));
  }
  
  /**
   * Ignore the sanctioning author
   */
  @Test
  public void testCompareSanctioning() throws Exception {
    Name p1 = new Name();
    Name p2 = new Name();
    
    p1.setCombinationAuthorship(parse("Fr. : Fr."));
    p2.getCombinationAuthorship().setAuthors(Lists.newArrayList("Fr."));
    
    assertEquals(Equality.EQUAL, comp.compare(p1, p2));
    
    p2.setCombinationAuthorship(parse("Fr. : Pers."));
    assertEquals(Equality.EQUAL, comp.compare(p1, p2));
    
    p1.getBasionymAuthorship().setAuthors(Lists.newArrayList("Mill."));
    p2.setBasionymAuthorship(parse("Mill. : Pers."));
    assertEquals(Equality.EQUAL, comp.compare(p1, p2));
  }
  
  @Test
  public void testCompareOptYear() throws Exception {
    assertAuth("Pallas, 1771", Equality.DIFFERENT, "Pimbus, 1771");
    assertAuth("Pallas, 1771", Equality.EQUAL, "1771");
    assertAuth("Pallas, 1771", Equality.EQUAL, "Pallas");
    assertAuth("Pallas, 1771", Equality.DIFFERENT, "1778");
    assertAuth("Pallas", Equality.UNKNOWN, "1771");
  }
  
  @Test
  public void testCompare() throws Exception {
    // https://github.com/gbif/checklistbank/issues/196
    assertAuth("Quél.", null, Equality.EQUAL, "Quel.", null);

    assertAuth("Debreczy & I. Rácz", null, Equality.EQUAL, "Rácz", null);
    assertAuth("DC. ex Lam. et DC.", null, Equality.EQUAL, "DC.", null);
    
    assertAuth(null, null, Equality.UNKNOWN, null, null);
    assertAuth("", "  ", Equality.UNKNOWN, " ", "   ");
    assertAuth("L.", null, Equality.UNKNOWN, null, null);
    
    assertAuth("Bluff & Fingerh.", null, Equality.DIFFERENT, "Lindl.", null);
    assertAuth("Lindl.", null, Equality.EQUAL, "Lindl.", null);
    
    assertAuth(null, "1978", Equality.DIFFERENT, null, "1934");
    assertAuth(null, "1978", Equality.EQUAL, null, "1978");
    
    assertAuth("H. Christ", null, Equality.DIFFERENT, "C. Chr.", null);
    assertAuth("Reichenbach", "1837", Equality.EQUAL, "Abasicarpon Andrz. ex Rchb.", null);
    
    assertAuth("Torr et Gray", null, Equality.EQUAL, "Torr. & A.Gray", null);
    assertAuth("A.Murr", "1863", Equality.EQUAL, "A.Murray", null);
    assertAuth("Maxim.", null, Equality.EQUAL, "Max.", null);
    
    assertAuth("A.Murr", "1863", Equality.EQUAL, "A. Murray", null);
    assertAuth("A.Murr", "1863", Equality.EQUAL, "A.Murray", null);
    assertAuth("A.Murr", "1863", Equality.EQUAL, "A. Murr.", null);
    assertAuth("A.Murr", "1863", Equality.DIFFERENT, "B. Murr.", null);
    
    assertAuth("Debreczy & I. Rácz", null, Equality.EQUAL, "Rácz", null);
    assertAuth("Debreczy & I. Rácz", null, Equality.EQUAL, "Debreczy", null);
    
    assertAuth("White, Herbert & Harvey", null, Equality.EQUAL, "A.J. White, Herbert et P.J. Harvey", null);
    assertAuth("A.J.White", null, Equality.EQUAL, "A.J. White, Herbert et P.J. Harvey", null);
    assertAuth("Harvey", null, Equality.EQUAL, "A.J. White, Herbert et P.J. Harvey", null);
    
    assertAuth("R.H.Roberts", null, Equality.DIFFERENT, "R.J.Roberts", null);
    assertAuth("V.J.Chapm.", null, Equality.DIFFERENT, "F.R.Chapm.", null);
    assertAuth("V.J.Chapm.", null, Equality.DIFFERENT, "F.Chapm.", null);
    assertAuth("Chapm.", null, Equality.EQUAL, "F.R.Chapm.", null);
    assertAuth("Chapm.", null, Equality.EQUAL, "A.W.Chapm.", null);
    
    assertAuth("Brot. ex Willk. & Lange", null, Equality.DIFFERENT, "L.", null);
    
    assertAuth("Brugg.", null, Equality.EQUAL, "Brug.", null);
    assertAuth("A.Bruggen.", null, Equality.EQUAL, "Brug.", null);
    assertAuth("Brug.", null, Equality.EQUAL, "Pascal Bruggeman", null);
    
    assertAuth("Presl ex DC.", null, Equality.EQUAL, "C. Presl ex de Candolle", null);
    
    // https://github.com/gbif/checklistbank/issues/7
    assertAuth("G. Don f.", null, Equality.EQUAL, "G. Don fil.", null);
    assertAuth("Don f.", null, Equality.EQUAL, "Don fil.", null);
    assertAuth("F.K. Schimp. et Spenn.", null, Equality.EQUAL, "K.F. Schimp. et Spenn.", null);
    assertAuth("J.A. Weinm.", null, Equality.EQUAL, "Weinm.", null);
    assertAuth("DC. ex Lam. et DC.", null, Equality.EQUAL, "DC.", null);
    
    assertAuth("Koch", null, Equality.EQUAL, "Johann Friedrich Wilhelm Koch", null);
    assertAuth("Koch", null, Equality.EQUAL, "J F W Koch", null);
    assertAuth("Koch", null, Equality.EQUAL, "H Koch", null);
    
    assertAuth("L.f", null, Equality.EQUAL, "Linnaeus filius", null);
    assertAuth("L. f", null, Equality.EQUAL, "Linnaeus filius", null);
    assertAuth("L.fil.", null, Equality.EQUAL, "Linnaeus filius", null);
    
    assertAuth("Schultz-Bip", null, Equality.EQUAL, "Sch.Bip.", null);
    
    assertAuth("Bruand", "1850", Equality.EQUAL, "Bruand", "1850");
    assertAuth("Bruand", "1850", Equality.DIFFERENT, "Bruand", "1851");
    assertAuth("Bruand", "1850", Equality.DIFFERENT, null, "1998");
    assertAuth("Bruand", "1850", Equality.EQUAL, null, "1850");
    assertAuth("Bruand", "1850", Equality.EQUAL, "Bruand", null);
    
    // https://github.com/gbif/checklistbank/issues/2
    assertAuth("L. f.", null, Equality.EQUAL, "L.", null);
    assertAuth("L.", null, Equality.EQUAL, "L.fil.", null);
    assertAuth("L. Bolus", null, Equality.EQUAL, "L. Bol.", null);
  }
  
  @Test
  public void testCompareStrict() throws Exception {
    assertFalse(comp.compareStrict(null, null));
    assertFalse(comp.compareStrict(new Authorship(), new Authorship()));
    assertFalse(comp.compareStrict(null, new Authorship()));
    
    assertAuthStrict("", "  ", false, " ", "   ");
    assertAuthStrict("L.", null, false, null, null);
    
    assertAuthStrict("Bluff & Fingerh.", null, false, "Lindl.", null);
    assertAuthStrict("Lindl.", null, true, "Lindl.", null);
    
    assertAuthStrict(null, "1978", false, null, "1934");
    assertAuthStrict(null, "1978", false, null, "1978");
    
    assertAuthStrict("H. Christ", null, false, "C. Chr.", null);
    assertAuthStrict("Reichenbach", "1837", false, "Abasicarpon Andrz. ex Rchb.", null);
    assertAuthStrict("Reichenbach", null, true, "Abasicarpon Andrz. ex Rchb.", null);
    assertAuthStrict("Reichenbach", "1837", true, "Abasicarpon Andrz. ex Rchb.", "1837");
    
    assertAuthStrict("Torr et Gray", null, true, "Torr. & A.Gray", null);
    
    assertAuthStrict("Boed.", null, true, "Boed.", null);
    assertAuthStrict("Boed.", null, false, "F.Boos", null);
    assertAuthStrict("Boed.", null, false, "Boott", null);
    assertAuthStrict("Boed.", null, false, "F.Bolus", null);
    assertAuthStrict("Boed.", null, false, "Borchs.", null);
    
    assertAuthStrict("Hett.", null, false, "Scheffers", null);
    assertAuthStrict("Hett.", null, false, "Schew.", null);
    assertAuthStrict("Hett.", null, false, "Schemmann", null);
    assertAuthStrict("Hett.", null, false, "W.Mast.", null);
    assertAuthStrict("Hett.", null, false, "Kirschst.", null);
    
    
    /**
     * http://dev.gbif.org/issues/browse/POR-398
     */
    assertAuthStrict("Ridgway", "1893", true, "Ridgway", "1893");
    assertAuthStrict("K. Koch", null, true, "K. Koch", null);
    assertAuthStrict("K.Koch", null, true, "K. Koch", null);
    assertAuthStrict("A. Nelson", null, true, "A Nélson", null);
    assertAuthStrict("Colla", null, true, "Bertero ex Colla", null);
    assertAuthStrict("Taczanowski & Berlepsch", "1885", true, "Berlepsch & Taczanowski", "188?");
    
    assertAuthStrict("Oberholser", "1917", false, "Oberholser", "1919");
    assertAuthStrict("Gould", "1860", false, "Gould", "1862");
    assertAuthStrict("Gould", "1860", false, "Gould", "1863");
    assertAuthStrict("A. Nelson", null, false, "E.E. Nelson", null);
    
    assertAuthStrict("Koch", "1897", false, "K. Koch", null);
    assertAuthStrict("Koch", null, false, "K. Koch", null);
    assertAuthStrict("J Koch", null, true, "Koch", null);
    assertAuthStrict("Koch", null, true, "Johann Friedrich Wilhelm Koch", null);
    assertAuthStrict("Koch", null, true, "J F W Koch", null);
    assertAuthStrict("Koch.", null, false, "H Koch", null);
    
    assertAuthStrict("Taczanowski & Berlepsch", "1885", false, "Berlepsch & Taczanowski", "1883");
    assertAuthStrict("Taczanowski & Berlepsch", "1885", true, "Berlepsch & Taczanowski", "1885");
    
    assertAuthStrict("Chapm.", null, false, "F.R.Chapm.", null);
    assertAuthStrict("Chapm.", null, true, "A.W.Chapm.", null);
    
    assertAuthStrict("Brugg.", null, true, "Brug.", null);
    assertAuthStrict("A.Bruggen.", null, false, "Brug.", null);
    assertAuthStrict("Brug.", null, true, "Pascal Bruggeman", null);
    
  }
  
  @Test
  public void testEqualsWithBasionym() throws Exception {
    assertAuth("Maxim.", null, "Trautv. ex Maxim.", null, Equality.EQUAL, "Maxim.", null, null, null);
    assertAuth("Maxim.", null, "Trautv. ex Karl Johann Maximowicz", null, Equality.EQUAL, "Max.", null, null, null);
    assertAuth("Maxim.", null, null, null, Equality.EQUAL, "Karl Johann Maximowicz", null, null, null);
    
    assertAuth("Bluff & Fingerh.", null, "L.", null, Equality.DIFFERENT, "Mill.", "1768", null, null);
    assertAuth("Mill.", null, "L.", null, Equality.EQUAL, "Mill.", "1768", null, null);
    
    assertAuth("Debreczy & I. Rácz", null, null, null, Equality.EQUAL, "Debreczy & Rácz", null, null, null);
    assertAuth("Debreczy & I.Rácz", null, null, null, Equality.DIFFERENT, "Silba", null, "Debreczy & I.Rácz", null);
    
    assertAuth(null, null, "Pauly", "1986", Equality.EQUAL, null, null, "Pauly", "1986");
    assertAuth(null, null, "Moure", "1956", Equality.DIFFERENT, null, null, "Pauly", "1986");
    // missing brackets is a common error so make this a positive comparison!
    assertAuth("Pauly", "1986", null, null, Equality.EQUAL, null, null, "Pauly", "1986");
    
    assertAuth("Erichson", "1847", null, null, Equality.UNKNOWN, null, null, "Linnaeus", "1758");
  }
  
  @Test
  public void testEqualsSubstring() throws Exception {
    Name p1 = new Name();
    Name p2 = new Name();
    
    p1.getCombinationAuthorship().setAuthors(Lists.newArrayList("L."));
    ;
    p2.getCombinationAuthorship().setAuthors(Lists.newArrayList("Linne"));
    ;
    assertEquals(Equality.EQUAL, comp.compare(p1, p2));
    
    p2.getCombinationAuthorship().setAuthors(Lists.newArrayList("Lin."));
    ;
    assertEquals(Equality.EQUAL, comp.compare(p1, p2));
    
    p1.getCombinationAuthorship().setAuthors(Lists.newArrayList("DC."));
    ;
    p2.getCombinationAuthorship().setAuthors(Lists.newArrayList("De Candolle"));
    ;
    assertEquals(Equality.EQUAL, comp.compare(p1, p2));
    
    p1.getCombinationAuthorship().setAuthors(Lists.newArrayList("Miller"));
    ;
    p2.getCombinationAuthorship().setAuthors(Lists.newArrayList("Mill."));
    ;
    assertEquals(Equality.EQUAL, comp.compare(p1, p2));
    
    p1.getCombinationAuthorship().setAuthors(Lists.newArrayList("Hern."));
    ;
    p2.getCombinationAuthorship().setAuthors(Lists.newArrayList("Hernandez"));
    ;
    assertEquals(Equality.EQUAL, comp.compare(p1, p2));
    
    p1.setCombinationAuthorship(parse("Robertson, T., Miller, P. et Jameson, R. J."));
    ;
    p2.getCombinationAuthorship().setAuthors(Lists.newArrayList("Miller"));
    ;
    assertEquals(Equality.EQUAL, comp.compare(p1, p2));
    
    p1.setCombinationAuthorship(parse("T. Robertson, P. Miller & R.J. Jameson"));
    ;
    p2.getCombinationAuthorship().setAuthors(Lists.newArrayList("Miller"));
    ;
    assertEquals(Equality.EQUAL, comp.compare(p1, p2));
    
    p2.getCombinationAuthorship().setAuthors(Lists.newArrayList("Mülles"));
    ;
    assertEquals(Equality.DIFFERENT, comp.compare(p1, p2));
  }
  
  
  @Test
  public void testBlattariaAuthors() throws Exception {
    Name p1 = new Name();
    p1.getCombinationAuthorship().setAuthors(Lists.newArrayList("P. Miller"));
    ;
    p1.getCombinationAuthorship().setYear("1754");
    
    Name p2 = new Name();
    p2.getCombinationAuthorship().setAuthors(Lists.newArrayList("O. Kuntze"));
    ;
    p2.getCombinationAuthorship().setYear("1891");
    
    Name p3 = new Name();
    p3.getCombinationAuthorship().setAuthors(Lists.newArrayList("Voet, ?"));
    ;
    p3.getCombinationAuthorship().setYear("1806");
    
    Name p4 = new Name();
    p4.getCombinationAuthorship().setAuthors(Lists.newArrayList("Weyenbergh"));
    ;
    p4.getCombinationAuthorship().setYear("1874");
    
    List<Name> names = Lists.newArrayList(p1, p2, p3, p4);
    for (Name p : names) {
      if (!p1.equals(p)) {
        assertEquals(Equality.DIFFERENT, comp.compare(p1, p));
      }
      if (!p2.equals(p)) {
        assertEquals(Equality.DIFFERENT, comp.compare(p2, p));
      }
      if (!p3.equals(p)) {
        assertEquals(Equality.DIFFERENT, comp.compare(p3, p));
      }
      if (!p4.equals(p)) {
        assertEquals(Equality.DIFFERENT, comp.compare(p4, p));
      }
    }
  }
  
  @Test
  public void testUnparsedAuthors() throws Exception {
    Name p3 = new Name();
    p3.setScientificName("Blattaria Voet, ?, 1806");
    p3.setGenus("Blattaria");
    
    Name p4 = new Name();
    p4.setScientificName("Blattaria Weyenbergh, 1874");
    p4.setGenus("Blattaria");
    
    assertEquals(Equality.UNKNOWN, comp.compare(p3, p4));
  }
  
  @Test
  public void testAlikeAuthors() throws Exception {
    Name p1 = new Name();
    p1.getCombinationAuthorship().setAuthors(Lists.newArrayList("Voet"));
    ;
    
    Name p2 = new Name();
    p2.getCombinationAuthorship().setAuthors(Lists.newArrayList("Weyenbergh"));
    ;
    
    assertEquals(Equality.DIFFERENT, comp.compare(p1, p2));
    
    p2 = new Name();
    p2.getCombinationAuthorship().setAuthors(Lists.newArrayList("Voetington"));
    ;
    
    assertEquals(Equality.EQUAL, comp.compare(p1, p2));
    
    p2 = new Name();
    p2.getCombinationAuthorship().setAuthors(Lists.newArrayList("Vellington"));
    ;
    
    assertEquals(Equality.DIFFERENT, comp.compare(p1, p2));
  }
  
  /**
   * see http://dev.gbif.org/issues/browse/PF-2445
   */
  @Test
  public void testTransliterations() throws Exception {
    Name p1 = new Name();
    p1.getCombinationAuthorship().setAuthors(Lists.newArrayList("Müller"));
    ;
    
    Name p2 = new Name();
    p2.getCombinationAuthorship().setAuthors(Lists.newArrayList("Muller"));
    ;
    
    Name p3 = new Name();
    p3.getCombinationAuthorship().setAuthors(Lists.newArrayList("Mueller"));
    ;
    
    assertEquals(Equality.EQUAL, comp.compare(p1, p2));
    assertEquals(Equality.EQUAL, comp.compare(p1, p3));
    assertEquals(Equality.EQUAL, comp.compare(p2, p3));
    
    
    p1.getCombinationAuthorship().setAuthors(Lists.newArrayList("Müll."));
    ;
    p2.getCombinationAuthorship().setAuthors(Lists.newArrayList("Mull"));
    ;
    p3.getCombinationAuthorship().setAuthors(Lists.newArrayList("Muell"));
    ;
    
    assertEquals(Equality.EQUAL, comp.compare(p1, p2));
    assertEquals(Equality.EQUAL, comp.compare(p1, p3));
    assertEquals(Equality.EQUAL, comp.compare(p2, p3));
  }
  
  private void assertAuth(String a1, String y1, Equality eq, String a2, String y2) throws InterruptedException {
    assertEquals(eq, comp.compare(parse(a1, y1), parse(a2, y2)));
  }
  
  private void assertAuth(String a1, Equality eq, String a2) throws InterruptedException {
    assertEquals(eq, comp.compare(parse(a1), parse(a2)));
  }
  
  private void assertAuthStrict(String a1, String y1, boolean eq, String a2, String y2) throws InterruptedException {
    assertEquals(eq, comp.compareStrict(parse(a1, y1), parse(a2, y2)));
  }
  
  private void assertAuth(String a1, String y1, String a1b, String y1b, Equality eq, String a2, String y2, String a2b, String y2b) throws InterruptedException {
    Name p1 = new Name();
    p1.setCombinationAuthorship(parse(a1, y1));
    p1.setBasionymAuthorship(parse(a1b, y1b));
    
    Name p2 = new Name();
    p2.setCombinationAuthorship(parse(a2, y2));
    p2.setBasionymAuthorship(parse(a2b, y2b));
    
    assertEquals(eq, comp.compare(p1, p2));
  }
}