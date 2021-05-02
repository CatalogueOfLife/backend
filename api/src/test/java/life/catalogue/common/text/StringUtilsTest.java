package life.catalogue.common.text;

import life.catalogue.api.search.NameUsageSearchParameter;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 */
public class StringUtilsTest {


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
  public void testDecodeUtf8Garbage() {
    assertUtf8(null, null);
    assertUtf8("", "");
    assertUtf8("a", "a");
    assertUtf8("ä-üOØ", "ä-üOØ");
    assertUtf8("(Günther, 1887)", "(GÃ¼nther, 1887)");
    assertUtf8("Böhlke, 1955", "BÃ¶hlke, 1955");
    assertUtf8("Nielsen & Quéro, 1991\n", "Nielsen & QuÃ©ro, 1991\n");
    assertUtf8("Rosinés", "RosinÃ©s");
    assertUtf8("S. Calderón & Standl.", "S. CalderÃ³n & Standl.");
    assertUtf8("Strömman, 1896", "StrÃ¶mman, 1896");
    assertUtf8("Sérus.", "SÃ©rus.");
    assertUtf8("Thér.", "ThÃ©r.");
    assertUtf8("Trécul", "TrÃ©cul");
    assertUtf8("Hale & López-Fig.\n", "Hale & LÃ³pez-Fig.\n");
  }

  private void assertUtf8(String expected, String src) {
    String decoded = StringUtils.decodeUtf8Garbage(src);
    assertEquals(expected, decoded);
    // make sure if we had gotten the correct string it would not be modified
    assertEquals(expected, StringUtils.decodeUtf8Garbage(decoded));
  }

  @Test
  public void testFoldToAscii() throws Exception {
    assertEquals("Navas, 1929", StringUtils.foldToAscii("Navás, 1929"));
    assertEquals(null, StringUtils.foldToAscii(null));
    assertEquals("", StringUtils.foldToAscii(""));
    assertEquals("Schulhof, Gymnasium Hurth", StringUtils.foldToAscii("Schulhof, Gymnasium Hürth"));
    assertEquals("Doring", StringUtils.foldToAscii("Döring"));
    assertEquals("Desireno", StringUtils.foldToAscii("Désírèñø"));
    assertEquals("Debreczy & I. Racz", StringUtils.foldToAscii("Debreçzÿ & Ï. Rácz"));
    assertEquals("Donatia novae-zelandiae", StringUtils.foldToAscii("Donatia novae-zelandiæ"));
    assertEquals("Carex ×cayouettei", StringUtils.foldToAscii("Carex ×cayouettei"));
    assertEquals("Carex comosa × Carex lupulina", StringUtils.foldToAscii("Carex comosa × Carex lupulina"));
    assertEquals("Aeropyrum coil-shaped virus", StringUtils.foldToAscii("Aeropyrum coil-shaped virus"));
    assertEquals("†Lachnus bonneti", StringUtils.foldToAscii("†Lachnus bonneti"));

    assertEquals("lachs", StringUtils.foldToAscii("łachs"));
    assertEquals("Coccinella 2-pustulata", StringUtils.foldToAscii("Coccinella 2-puſtulata"));

    String test = "ŠŒŽšœžŸ¥µÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝßàáâãäåæçèéêëìíîïðñòóôõöøùúûüýÿ";
    assertEquals("SOEZsoezY¥µAAAAAAAECEEEEIIIIDNOOOOOOUUUUYssaaaaaaaeceeeeiiiidnoooooouuuuyy", StringUtils.foldToAscii(test));
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
}