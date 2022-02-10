package life.catalogue.parser;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

/**
 * Parses integers throwing UnparsableException in case the value is not empty but unparsable.
 */
public class IntegerParser implements Parser<Integer> {
  public static final IntegerParser PARSER = new IntegerParser();

  @Override
  public Optional<Integer> parse(String value) throws UnparsableException {
    if (StringUtils.isBlank(value)) {
      return Optional.empty();
    }
    value = value.replaceAll("\\s", "");
    try {
      return Optional.of(Integer.valueOf(value));
    } catch (NumberFormatException e) {
    }
    throw new UnparsableException(Double.class, value);
  }
}
