package life.catalogue.matching.person.harvest;

import org.junit.Test;

import static org.junit.Assert.*;

public class YearsTest {

  @Test
  public void ipniDates() {
    assertEquals(new Years.Span(1757, 1822, null, null), Years.ipni("1757-1822"));
    assertEquals(new Years.Span(1967, null, null, null), Years.ipni("1967-"));
    assertEquals(new Years.Span(null, null, 1980, 1980), Years.ipni("fl. 1980"));
    assertEquals(new Years.Span(null, null, 1977, null), Years.ipni("fl. 1977-"));
    assertEquals(new Years.Span(null, null, 1850, 1870), Years.ipni("fl. 1850-1870"));
    assertEquals(new Years.Span(1950, null, null, null), Years.ipni("b. 1950"));
    assertEquals(new Years.Span(null, 1890, null, null), Years.ipni("d. 1890"));
    assertEquals(new Years.Span(1757, 1822, null, null), Years.ipni("(c. 1757-1822?)"));
    assertEquals(Years.NONE, Years.ipni(""));
    assertEquals(Years.NONE, Years.ipni(null));
    assertEquals(Years.NONE, Years.ipni("19th century"));
  }

  @Test
  public void wikidataTimes() {
    assertEquals(Integer.valueOf(1788), Years.wikidata("1788-01-01T00:00:00Z", 11));
    assertEquals(Integer.valueOf(1788), Years.wikidata("+1788-06-11T00:00:00Z", 9));
    assertNull(Years.wikidata("-0350-01-01T00:00:00Z", 9));
    assertNull(Years.wikidata("t123456", 11));
    assertNull(Years.wikidata(null, 11));
  }

  /** "+2000-00-00" with precision 7 is the 20th century, not the year 2000: anything vaguer than a year is no year */
  @Test
  public void wikidataPrecisionBelowAYear() {
    assertNull(Years.wikidata("+2000-00-00T00:00:00Z", 7));
    assertNull(Years.wikidata("+1850-00-00T00:00:00Z", 8));
  }
}
