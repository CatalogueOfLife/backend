package life.catalogue.common.util;

import java.util.regex.Matcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegexUtils {
  private static final Logger LOG = LoggerFactory.getLogger(RegexUtils.class);

  public static void log(Matcher matcher) {
    int i = -1;
    while (i < matcher.groupCount()) {
      i++;
      LOG.debug("  {}: >{}<", i, matcher.group(i));
    }
  }
}
