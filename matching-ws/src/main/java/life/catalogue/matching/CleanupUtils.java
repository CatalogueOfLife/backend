package life.catalogue.matching;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.Normalizer;
import java.util.regex.Pattern;

public class CleanupUtils {
//  private static final Logger LOG = LoggerFactory.getLogger(CleanupUtils.class);
  private static final Pattern NULL_PATTERN = Pattern.compile("^\\s*(\\\\N|\\\\?NULL|null)\\s*$");
  private static final CharMatcher SPACE_MATCHER = CharMatcher.whitespace().or(CharMatcher.javaIsoControl());

//  public static void registerCleanupHook(final File f) {
//    Runtime.getRuntime().addShutdownHook(new Thread() {
//
//      @Override
//      public void run() {
//        if (f.exists()) {
//          LOG.debug("Deleting file {}", f.getAbsolutePath());
//          FileUtils.deleteQuietly(f);
//        }
//      }
//    });
//  }

  /**
   * Does a conservative, generic cleaning of strings including:
   *  - trims and replaces various whitespace and invisible control characters
   *  - remove common verbatim values for NULL
   *  - normalises unicode into the NFC form
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

}
