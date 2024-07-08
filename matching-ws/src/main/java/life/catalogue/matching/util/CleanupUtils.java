package life.catalogue.matching.util;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import life.catalogue.matching.model.LinneanClassification;

import life.catalogue.parser.RankParser;
import life.catalogue.parser.UnparsableException;

import org.apache.commons.lang3.StringUtils;

import org.gbif.nameparser.api.Rank;

import java.text.Normalizer;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Utility class to clean up strings and other objects, typically from
 * request parameters or other user input.
 */
public class CleanupUtils {

  private static final Pattern NULL_PATTERN = Pattern.compile("^\\s*(\\\\N|\\\\?NULL|null)\\s*$");
  private static final CharMatcher SPACE_MATCHER =
      CharMatcher.whitespace().or(CharMatcher.javaIsoControl());

  /**
   * Does a conservative, generic cleaning of strings including: - trims and replaces various
   * whitespace and invisible control characters - remove common verbatim values for NULL -
   * normalises unicode into the NFC form
   */
  public static String clean(String x) {
    if (Strings.isNullOrEmpty(x) || NULL_PATTERN.matcher(x).find()) {
      return null;
    }
    x = SPACE_MATCHER.trimAndCollapseFrom(x, ' ');
    // normalise unicode into NFC
    x = Normalizer.normalize(x, Normalizer.Form.NFC);
    return Strings.emptyToNull(x.trim());
  }

  /**
   * @param classification the classification object to clean
   * @return a new cleaned classification object
   */
  public static LinneanClassification clean(LinneanClassification classification) {
    classification.setKingdom(clean(classification.getKingdom()));
    classification.setPhylum(clean(classification.getPhylum()));
    classification.setClazz(clean(classification.getClazz()));
    classification.setOrder(clean(classification.getOrder()));
    classification.setFamily(clean(classification.getFamily()));
    classification.setGenus(clean(classification.getGenus()));
    classification.setSubgenus(clean(classification.getSubgenus()));
    classification.setSpecies(clean(classification.getSpecies()));
    return classification;
  }

  public static String removeNulls(String value) {
    if (value == null) {
      return null;
    }
    value = StringUtils.trimToEmpty(value);
    return "null".equalsIgnoreCase(value) ? null : value;
  }

  public static boolean bool(Boolean bool) {
    return bool != null && bool;
  }

  public static String first(String... values) {
    if (values != null) {
      for (String val : values) {
        if (!StringUtils.isBlank(val)) {
          return val;
        }
      }
    }
    return null;
  }

  /**
   * Parses a rank from a string value. The value is trimmed and uppercased before parsing.
   *
   * @param value the rank value to parse
   * @return Rank the parsed rank or null if the value is null or empty
   */
  public static Rank parseRank(String value) {
    try {
      if (!Objects.isNull(value) && !value.isEmpty()) {
        Optional<Rank> pr = RankParser.PARSER.parse(null, value);
        if (pr.isPresent()) {
          return Rank.valueOf(pr.get().name());
        }
      }
    } catch (UnparsableException e) {
      // throw new UnparsableException("Rank", value);
    }
    return null;
  }
}
