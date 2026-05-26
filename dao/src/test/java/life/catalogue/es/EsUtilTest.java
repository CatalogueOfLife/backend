package life.catalogue.es;

import java.util.regex.Pattern;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit-level checks for {@link EsUtil} helpers that build the ES index schema. These do not
 * require a running ES cluster or Docker.
 */
public class EsUtilTest {

  @Test
  public void schemaPlaceholderSubstituted() throws Exception {
    String schema = EsUtil.loadSchemaJson();
    assertFalse("loaded schema still contains the decompound placeholder",
      schema.contains(EsUtil.SCINAME_DECOMPOUND_PLACEHOLDER));
    // sanity: the regex anchor is present
    assertTrue("loaded schema is missing the substituted decompound pattern",
      schema.contains("^(?:") && schema.contains(")([a-z]{4,})$"));
  }

  @Test
  public void decompoundPatternIsLengthDescThenAlpha() throws Exception {
    String regex = EsUtil.buildScinameDecompoundPattern();
    // peel off the surrounding ^(?: ... )([a-z]{4,})$ wrapper
    String body = regex.substring(4, regex.indexOf(")([a-z]{4,})$"));
    String[] parts = body.split("\\|");

    // order check: each adjacent pair must satisfy length-desc-then-alpha
    for (int i = 1; i < parts.length; i++) {
      String a = parts[i - 1];
      String b = parts[i];
      if (a.length() == b.length()) {
        assertTrue("alternatives at length " + a.length() + " out of alpha order: '" + a + "' then '" + b + "'",
          a.compareTo(b) < 0);
      } else {
        assertTrue("alternatives out of length-desc order: '" + a + "' (len " + a.length() + ") then '" + b + "' (len " + b.length() + ")",
          a.length() > b.length());
      }
    }
  }

  @Test
  public void decompoundRegexCompilesAndMatches() throws Exception {
    Pattern p = Pattern.compile(EsUtil.buildScinameDecompoundPattern());
    // sub-mucidus -> stem "mucidus"
    var m1 = p.matcher("submucidus");
    assertTrue("submucidus should match the decompound regex", m1.matches());
    assertEquals("mucidus", m1.group(1));
    // pseudo-mucidus -> stem "mucidus"
    var m2 = p.matcher("pseudomucidus");
    assertTrue("pseudomucidus should match the decompound regex", m2.matches());
    assertEquals("mucidus", m2.group(1));
  }

  @Test
  public void decompoundRegexRespectsMinStemLength() throws Exception {
    Pattern p = Pattern.compile(EsUtil.buildScinameDecompoundPattern());
    // 'diploid': diplo + id (id is 2 chars, below the [a-z]{4,} minimum) -> no match
    assertFalse("diploid should not match: stem 'id' is below min length",
      p.matcher("diploid").matches());
    // 'submoun': sub + moun (4 chars) -> match
    var m = p.matcher("submoun");
    assertTrue("submoun should match: stem 'moun' meets the 4-char minimum", m.matches());
    assertEquals("moun", m.group(1));
  }
}
