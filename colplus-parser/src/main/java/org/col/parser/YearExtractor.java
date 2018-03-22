package org.col.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.col.parser.FuzzyDateParser.DateStringFilter;

/**
 * A DateStringFilter implementation used as a course, last ditch attempt to at least extract a year
 * from date strings like "2007b" or "1914 - 1918"
 *
 */
public class YearExtractor implements DateStringFilter {

  private static final int MIN_YEAR = 1500;
  private static final int MAX_YEAR = 2100;

  private static final Pattern PATTERN = Pattern.compile("(^|\\D+)(\\d{4})($|\\D+)");

  public YearExtractor() {}

  @Override
  public String filter(String dateString) {
    Matcher matcher = PATTERN.matcher(dateString);
    if (matcher.find()) {
      String filtered = matcher.group(2);
      int year = Integer.parseInt(filtered);
      if (year >= MIN_YEAR && year <= MAX_YEAR) {
        return filtered;
      }
    }
    return null;
  }

}
