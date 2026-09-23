package life.catalogue.matching.person.harvest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Reads the years of a person from what the authorities write.
 */
final class Years {
  record Span(@Nullable Integer born, @Nullable Integer died, @Nullable Integer activeFrom, @Nullable Integer activeTo) {
  }

  static final Span NONE = new Span(null, null, null, null);
  private static final Pattern FLORUIT = Pattern.compile("^fl\\.?\\s*(\\d{4})s?\\s*(-\\s*(\\d{4})?)?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern BORN = Pattern.compile("^b\\.?\\s*(\\d{4})$", Pattern.CASE_INSENSITIVE);
  private static final Pattern DIED = Pattern.compile("^d\\.?\\s*(\\d{4})$", Pattern.CASE_INSENSITIVE);
  private static final Pattern LIFE = Pattern.compile("^(\\d{4})?\\s*-\\s*(\\d{4})?$");
  private static final Pattern WIKIDATA = Pattern.compile("^\\+?(\\d{1,4})-\\d\\d-\\d\\dT");

  private Years() {
  }

  /**
   * @param dates IPNI's "1757-1822", "1967-", "fl. 1980", "fl. 1977-", "b. 1950", "d. 1890", with circa and question
   *              marks ignored. A single floruit year is a span of that year alone.
   */
  static Span ipni(@Nullable String dates) {
    if (dates == null) return NONE;
    String s = dates.replaceAll("(?i)\\b(?:ca?\\.|circa)\\s*", "")
      .replaceAll("[()\\[\\]?]", "")
      .replace('–', '-')
      .trim();
    Matcher m = FLORUIT.matcher(s);
    if (m.find()) {
      Integer from = Integer.valueOf(m.group(1));
      Integer to = m.group(2) == null ? from : m.group(3) == null ? null : Integer.valueOf(m.group(3));
      return new Span(null, null, from, to);
    }
    m = BORN.matcher(s);
    if (m.find()) return new Span(Integer.valueOf(m.group(1)), null, null, null);
    m = DIED.matcher(s);
    if (m.find()) return new Span(null, Integer.valueOf(m.group(1)), null, null);
    m = LIFE.matcher(s);
    if (m.find() && (m.group(1) != null || m.group(2) != null)) {
      return new Span(m.group(1) == null ? null : Integer.valueOf(m.group(1)), m.group(2) == null ? null : Integer.valueOf(m.group(2)), null, null);
    }
    return NONE;
  }

  /**
   * @return the year of a Wikidata time value like "+1788-01-01T00:00:00Z", null for a year before the common era
   */
  @Nullable
  static Integer wikidata(@Nullable String time) {
    if (time == null) return null;
    Matcher m = WIKIDATA.matcher(time);
    return m.find() ? Integer.valueOf(m.group(1)) : null;
  }
}
