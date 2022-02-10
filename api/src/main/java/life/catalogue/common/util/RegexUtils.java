package life.catalogue.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class RegexUtils {
  private static final Logger LOG = LoggerFactory.getLogger(RegexUtils.class);

  public static void log(Matcher matcher) {
    int i = -1;
    while (i < matcher.groupCount()) {
      i++;
      LOG.debug("  {}: >{}<", i, matcher.group(i));
    }
  }

  public static void validatePattern(String regex) throws IllegalArgumentException {
    try {
      Preconditions.checkArgument(regex != null && regex.replaceAll("[.+*\\[\\]{}-]", "").length() > 2, "Valid regex parameter of at least 3 non wildcard characters is required");
      Pattern.compile(regex);
    } catch (PatternSyntaxException e) {
      throw new IllegalArgumentException("Regex invalid: "+regex, e);
    }
  }
}
