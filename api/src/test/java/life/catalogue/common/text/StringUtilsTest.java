package life.catalogue.common.text;

import life.catalogue.api.search.NameUsageSearchParameter;

import org.gbif.nameparser.api.Rank;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 */
public class StringUtilsTest {


  @Test
  public void escapeBackslash() {
    assertEquals("", StringUtils.escapePgCopy("", false));
    assertEquals(" ", StringUtils.escapePgCopy(" ", false));
    assertEquals("my\\npferd", StringUtils.escapePgCopy("my\npferd", false));
    assertEquals("my\\npferd", StringUtils.escapePgCopy("my\npferd ", true));
    assertEquals("my\\\\npferd", StringUtils.escapePgCopy("my\\npferd ", true));
    assertEquals("my\\tpferd\\tno", StringUtils.escapePgCopy("my\tpferd\tno ", true));
    assertEquals(" user@domain.com|  hi mum   ", StringUtils.escapePgCopy(" user@domain.com|  hi mum   ", false));

    assertNull(StringUtils.escapePgCopy(null, false));
    assertNull(StringUtils.escapePgCopy(null, true));
    assertNull(StringUtils.escapePgCopy("", true));
    assertNull(StringUtils.escapePgCopy(" ", true));
    assertNull(StringUtils.escapePgCopy("   ", true));
  }

  @Test
  public void extractEmail() {
    assertEquals("user@domain.com", StringUtils.extractEmail("user@domain.com"));
    assertEquals("user@domain.co.in", StringUtils.extractEmail("user@domain.co.in"));
    assertEquals("user.name@domain.com", StringUtils.extractEmail("user.name@domain.com"));
    assertEquals("user?name@domain.co.in", StringUtils.extractEmail("user?name@domain.co.in"));
    assertEquals("user'name@domain.co.in", StringUtils.extractEmail("user'name@domain.co.in"));
    assertEquals("scratchpad@nhm.ac.uk", StringUtils.extractEmail("Scratchpad Team <scratchpad@nhm.ac.uk>"));
    assertEquals("dfgh@gbif.org", StringUtils.extractEmail(" dfgh @ gbif.org"));

    assertNull(StringUtils.extractEmail(" "));
    assertNull(StringUtils.extractEmail("@"));
    assertNull(StringUtils.extractEmail("@domain.co.in"));
  }

  @Test
  public void equalsIgnoreSpace() {
    assertFalse(StringUtils.equalsIgnoreCaseAndSpace(null, "gdu23"));
    assertFalse(StringUtils.equalsIgnoreCaseAndSpace("gdu23", null));
    assertTrue(StringUtils.equalsIgnoreCaseAndSpace(null, null));
    assertTrue(StringUtils.equalsIgnoreCaseAndSpace("gdu23", "gdu23"));

    assertTrue(StringUtils.equalsIgnoreCaseAndSpace("gDU23", "G du 23"));
    assertFalse(StringUtils.equalsIgnoreCaseAndSpace("gDU23", "G-du 23"));
  }

  @Test
  public void equalsIgnoreCase() {
    assertFalse(StringUtils.equalsIgnoreCase(null, "gdu23"));
    assertFalse(StringUtils.equalsIgnoreCase("gdu23", null));
    assertTrue(StringUtils.equalsIgnoreCase(null, null));
    assertTrue(StringUtils.equalsIgnoreCase("gdu23", "gdu23"));

    assertFalse(StringUtils.equalsIgnoreCase("gDU23", "G du 23"));
    assertFalse(StringUtils.equalsIgnoreCase("gDU23", "G-du 23"));
    assertTrue(StringUtils.equalsIgnoreCase("gDU23", "Gdu23"));
    assertTrue(StringUtils.equalsIgnoreCase("gDU23.#&", "Gdu23.#&"));
    assertFalse(StringUtils.equalsIgnoreCase("gDU23.#&", "Gdu23#&"));
  }

  @Test
  public void equalsIgnoreNonDigitLetters() {
    assertFalse(StringUtils.equalsDigitOrAsciiLettersIgnoreCase(null, "gdu23"));
    assertFalse(StringUtils.equalsDigitOrAsciiLettersIgnoreCase("gdu23", null));
    assertTrue(StringUtils.equalsDigitOrAsciiLettersIgnoreCase(null, null));
    assertTrue(StringUtils.equalsDigitOrAsciiLettersIgnoreCase("gdu23", "gdu23"));

    assertTrue(StringUtils.equalsDigitOrAsciiLettersIgnoreCase("g   DU 23", "G du 23"));
    assertTrue(StringUtils.equalsDigitOrAsciiLettersIgnoreCase("g DU.23", "G-du 23"));
    assertTrue(StringUtils.equalsDigitOrAsciiLettersIgnoreCase("gDU23", "Gdu23"));
    assertTrue(StringUtils.equalsDigitOrAsciiLettersIgnoreCase("gDU23.#&", "Gdu23.#&"));
    assertTrue(StringUtils.equalsDigitOrAsciiLettersIgnoreCase("L.", "L"));

    assertFalse(StringUtils.equalsDigitOrAsciiLettersIgnoreCase("gDU23.#&a", "Gdu23#&"));
  }

  @Test
  public void testRightSplit() {
    assertArrayEquals(new String[]{"Carl", "s"}, StringUtils.splitRight("Carlas", 'a'));
    assertArrayEquals(new String[]{"dwc:genus", "Abies"}, StringUtils.splitRight("dwc:genus:Abies", ':'));
  }
  
  @Test
  public void testIncrease() {
    assertEquals("Carlb", StringUtils.increase("Carla"));
    assertEquals("Homa", StringUtils.increase("Holz"));
    assertEquals("Aua", StringUtils.increase("Atz"));
    assertEquals("b", StringUtils.increase("a"));
    assertEquals("aa", StringUtils.increase("z"));
    assertEquals("AAA", StringUtils.increase("ZZ"));
    assertEquals("Aaa", StringUtils.increase("Zz"));
    assertEquals("aaa", StringUtils.increase("zz"));
    assertEquals("Abiet aaa", StringUtils.increase("Abies zzz"));
    assertEquals("Alle31.3-a ", StringUtils.increase("Alld31.3-z "));
    assertEquals("31.3-a a", StringUtils.increase("31.3-z "));
    assertEquals("aAaa", StringUtils.increase("zZz"));
    assertEquals("", StringUtils.increase(""));
    assertNull(StringUtils.increase(null));
  }


  @Test
  public void digitOrAsciiLetters() throws Exception {
    assertEquals("HALLO", StringUtils.digitOrAsciiLetters("Hallo"));
    assertEquals("HA LLO", StringUtils.digitOrAsciiLetters(" Ha-llo!"));
    assertEquals("HA LLO34 GRR", StringUtils.digitOrAsciiLetters(" Ha-llo34!   grr.!"));
  }

  @Test
  public void lowerCamelCase() {
    assertNull(StringUtils.lowerCamelCase(null));
    assertEquals("datasetKey", StringUtils.lowerCamelCase(NameUsageSearchParameter.DATASET_KEY));
    assertEquals("extinct", StringUtils.lowerCamelCase(NameUsageSearchParameter.EXTINCT));
    assertEquals("publishedInId", StringUtils.lowerCamelCase(NameUsageSearchParameter.PUBLISHED_IN_ID));
  }

  @Test
  public void concat() {
    assertNull(StringUtils.concat(", ", List.of()));
    assertEquals("A", StringUtils.concat(", ", List.of("A")));
    assertEquals("a, b, c", StringUtils.concat(", ", List.of("a", "b", "c")));
    assertEquals("1, 2, 3", StringUtils.concat(", ", List.of(1,2,3)));
    assertEquals("SPECIES, GENUS", StringUtils.concat(", ", List.of(Rank.SPECIES, Rank.GENUS)));
  }
}