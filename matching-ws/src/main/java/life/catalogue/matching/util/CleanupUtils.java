package life.catalogue.matching.util;

import life.catalogue.matching.model.ClassificationQuery;
import life.catalogue.parser.RankParser;
import life.catalogue.parser.UnparsableException;

import org.gbif.nameparser.api.Rank;

import java.text.Normalizer;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Utility class to clean up strings and other objects, typically from
 * request parameters or other user input.
 */
public class CleanupUtils {

  private static final Pattern NULL_PATTERN = Pattern.compile("^\\s*(\\\\N|\\\\?NULL|null)\\s*$");
  private static final Pattern BRACKET_PATTERN = Pattern.compile("(\\[.+]|\\{.+\\})");
  private static final CharMatcher SPACE_MATCHER =
      CharMatcher.whitespace().or(CharMatcher.javaIsoControl());
  private static final Pattern FIRST_WORD = Pattern.compile("^(.+?)\\b");
  private static final List<Rank> HIGHER_RANKS;

  static {
    List<Rank> ranks = Lists.newArrayList(Rank.LINNEAN_RANKS);
    ranks.remove(Rank.SPECIES);
    HIGHER_RANKS = ImmutableList.copyOf(ranks);
  }

  /**
   * Does a conservative, generic cleaning of strings including: - trims and replaces various
   * whitespace and invisible control characters - remove common verbatim values for NULL -
   * normalises unicode into the NFC form
   */
  public static String clean(String x) {
    if (Strings.isNullOrEmpty(x) || NULL_PATTERN.matcher(x).find()) {
      return null;
    }
    // remove all content within square or curly brackets
    x = BRACKET_PATTERN.matcher(x).replaceAll("");
    x = SPACE_MATCHER.trimAndCollapseFrom(x, ' ');
    x = x.replaceAll("_+", " ");
    // normalise unicode into NFC
    x = Normalizer.normalize(x, Normalizer.Form.NFC);

    return Strings.emptyToNull(x.trim());
  }

  public static ClassificationQuery clean(ClassificationQuery cl) {
    for (Rank rank : HIGHER_RANKS) {
      if (cl.nameFor(rank) != null) {
        String val = CleanupUtils.clean(cl.nameFor(rank));
        if (val != null) {
          Matcher m = FIRST_WORD.matcher(val);
          if (m.find()) {
            cl.setHigherRank(m.group(1), rank);
          }
        }
      }
    }
    cl.setSpecies(clean(cl.getSpecies()));
    return cl;
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
