package life.catalogue.interpreter;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link InterpreterUtils#replaceHtml} skips Jsoup for text without any of the characters Jsoup treats specially.
 * That text must come out exactly as Jsoup returns it.
 */
public class ReplaceHtmlEquivalenceTest {

  @Test
  public void plainTextAsJsoup() {
    final Random rnd = new Random(20261106);
    // everything but < & NUL and CR, incl. html whitespace, other controls, quotes, markup lookalikes and non BMP chars
    final String chars = "aZ0 \t\n\f\u000B\u0001\u001F\u007F\u0085  　>\"'=/;#!?-éß中😀";
    List<String> values = new ArrayList<>(List.of(
      "Abies alba Mill.", "  leading", "trailing \n", "\f form feed \f", "a > b", "x=\"1\"", "-->", "]]>", "<!-- none -->".substring(1),
      "Found in 3 localities; see Smith (1999): 12-14.", " nbsp ", " line ", "#x41; not an entity", "😀 emoji"
    ));
    for (int i = 0; i < 20_000; i++) {
      StringBuilder sb = new StringBuilder();
      int len = rnd.nextInt(16);
      for (int j = 0; j < len; j++) {
        sb.append(chars.charAt(rnd.nextInt(chars.length())));
      }
      values.add(sb.toString());
    }
    int compared = 0;
    for (String x : values) {
      if (StringUtils.isBlank(x) || !StringUtils.containsNone(x, '<', '&', '\0', '\r')) continue;
      for (boolean md : new boolean[]{true, false}) {
        assertEquals(InterpreterUtils.parseHtml(x, md), InterpreterUtils.replaceHtml(x, md), () -> "Different result for >>" + x + "<<");
      }
      compared++;
    }
    assertEquals(true, compared > 10_000, "compared only " + compared);
  }

  @Test
  public void markupStillParsed() {
    assertEquals("bold & link", InterpreterUtils.replaceHtml("<b>bold</b> &amp; link", false));
    assertEquals("[link](http://x.org)", InterpreterUtils.replaceHtml("<a href='http://x.org'>link</a>", true));
    // text with a carriage return always goes through Jsoup
    assertEquals(InterpreterUtils.parseHtml("a\r\nb", false), InterpreterUtils.replaceHtml("a\r\nb", false));
  }
}
