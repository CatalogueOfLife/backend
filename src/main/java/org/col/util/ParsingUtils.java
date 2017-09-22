package org.col.util;

import org.gbif.common.parsers.NumberParser;

/**
 * Utility class that exposes various simple parsing functions.
 * Many external parsers will be wrapped and pooled here to provide a single point of access.
 */
public class ParsingUtils {

  public static Integer parseInteger(String x) {
    return NumberParser.parseInteger(x);
  }
}
