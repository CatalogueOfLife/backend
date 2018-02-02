package org.col.parser;

import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

/**
 * Parses integers throwing UnparsableException in case the value is not empty but unparsable.
 */
public class IntegerParser implements Parser<Integer> {
  public static final Parser<Integer> PARSER = new IntegerParser();

  @Override
  public Optional<Integer> parse(String value) throws UnparsableException {
    try {
      return Optional.of(Integer.valueOf(value));
    } catch (NumberFormatException e) {
      if (StringUtils.isBlank(value)) {
        throw new UnparsableException(e);
      }
    }
    return Optional.empty();
  }
}
