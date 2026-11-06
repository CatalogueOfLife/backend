package life.catalogue.csv;

import java.io.StringReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.univocity.parsers.common.AbstractParser;
import com.univocity.parsers.common.CommonParserSettings;
import com.univocity.parsers.common.TextParsingException;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.tsv.TsvParserSettings;

import static life.catalogue.common.io.TabReader.newParser;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks that the cheaper value cleaning and the small column buffer used to sniff a file's format
 * do not change any result.
 */
public class CsvReaderEquivalenceTest {
  private static final Pattern NULL_PATTERN = Pattern.compile("^\\s*(\\\\N|\\\\?NULL|null)\\s*$");
  private static final CharMatcher SPACE_MATCHER = CharMatcher.whitespace().or(CharMatcher.javaIsoControl());
  private final Random rnd = new Random(20261106);

  private static String oldClean(String x) {
    if (Strings.isNullOrEmpty(x) || NULL_PATTERN.matcher(x).find()) {
      return null;
    }
    x = SPACE_MATCHER.trimAndCollapseFrom(x, ' ');
    x = Normalizer.normalize(x, Normalizer.Form.NFC);
    return Strings.emptyToNull(x.trim());
  }

  private String random(String alphabet, int maxLen) {
    int len = rnd.nextInt(maxLen + 1);
    StringBuilder sb = new StringBuilder(len);
    for (int i = 0; i < len; i++) {
      sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
    }
    return sb.toString();
  }

  @Test
  public void clean() {
    List<String> values = new ArrayList<>(List.of("null", " NULL ", "\\N", "\\NULL", "\tnull\n", "nulls", "Null", "\\n", "N", " null"));
    String chars = "\\NnulLU \t\n\u000B\f\r \u0000aé";
    for (int i = 0; i < 200_000; i++) {
      values.add(random(chars, 8));
    }
    for (String x : values) {
      assertEquals(oldClean(x), CsvReader.clean(x), () -> "Different result for >>" + x + "<<");
      if (NULL_PATTERN.matcher(x).find()) {
        assertTrue(CsvReader.mayBeNull(x), x);
      }
    }
  }

  /**
   * A parser with a column limit just above the length of the sample parses it exactly like one with the configured 64M limit.
   */
  @Test
  public void sampleColumnLimit() {
    String chars = "ab\t,;|\"' \n";
    List<CommonParserSettings<?>> settings = new ArrayList<>();
    settings.add(CsvReader.tsvSetting());
    settings.add(CsvReader.csvSetting());
    for (char del : new char[]{'\t', ',', ';', '|'}) {
      for (char quote : new char[]{'"', '\'', '\0'}) {
        CsvParserSettings cfg = CsvReader.csvSetting();
        cfg.getFormat().setDelimiter(del);
        cfg.setDelimiterDetectionEnabled(false);
        cfg.getFormat().setQuote(quote);
        cfg.getFormat().setQuoteEscape(quote);
        cfg.setQuoteDetectionEnabled(false);
        settings.add(cfg);
      }
    }
    CsvParserSettings auto = CsvReader.csvSetting();
    auto.detectFormatAutomatically();
    settings.add(auto);

    for (int i = 0; i < 20; i++) {
      String sample = "a\tb,c;d|e\n" + random(chars, 60);
      for (CommonParserSettings<?> cfg : settings) {
        assertEquals(64 * 1024 * 1024, cfg.getMaxCharsPerColumn());
        String expected = parse(newParser(cfg), sample);
        CommonParserSettings<?> small = cfg instanceof CsvParserSettings c ? c.clone() : ((TsvParserSettings) cfg).clone();
        small.setMaxCharsPerColumn(sample.length() + 1);
        assertEquals(expected, parse(newParser(small), sample), sample);
      }
      // the settings discovered keep the real limit
      assertEquals(64 * 1024 * 1024, CsvReader.discoverFormat(sample.lines().toList()).getMaxCharsPerColumn());
    }
  }

  private static String parse(AbstractParser<?> parser, String sample) {
    try {
      StringBuilder sb = new StringBuilder();
      for (String[] row : parser.parseAll(new StringReader(sample))) {
        sb.append(String.join("|", java.util.Arrays.stream(row).map(String::valueOf).toList())).append('\n');
      }
      return sb.toString();
    } catch (TextParsingException e) {
      return "ERROR";
    }
  }
}
