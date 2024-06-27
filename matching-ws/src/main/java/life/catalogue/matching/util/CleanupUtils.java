package life.catalogue.matching.util;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import life.catalogue.matching.model.LinneanClassification;

import org.apache.commons.lang3.StringUtils;

import java.text.Normalizer;
import java.util.regex.Pattern;

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
}
