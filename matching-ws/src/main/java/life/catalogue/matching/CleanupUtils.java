package life.catalogue.matching;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
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
   *
   * @param classification
   * @return
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
}
