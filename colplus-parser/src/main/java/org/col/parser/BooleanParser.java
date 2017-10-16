package org.col.parser;

import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

/**
 * Parses integers throwing UnparsableException in case the value is not empty but unparsable.
 */
public class BooleanParser implements Parser<Boolean> {

  @Override
  public Optional<Boolean> parse(String value) throws UnparsableException {
    String x = StringUtils.trimToEmpty(value).toLowerCase();
    if (x.equals("true") || x.equals("t") || x.equals("1") || x.equals("yes") || x.equals("y")) {
      return Optional.of(true);
    }
    if (x.equals("false") || x.equals("f") || x.equals("0") || x.equals("-1") || x.equals("no") || x.equals("n")) {
      return Optional.of(false);
    }

    if (StringUtils.isBlank(x)) {
      throw new UnparsableException("Unknown boolean: " + value);
    }
    return Optional.empty();
  }
}
