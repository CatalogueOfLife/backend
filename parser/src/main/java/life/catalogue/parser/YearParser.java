package life.catalogue.parser;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

/**
 * Parses years leniently using the YearExtractor.
 */
public class YearParser implements Parser<Integer> {
  public static final YearParser PARSER = new YearParser();
  private final static YearExtractor EXTRACTOR = new YearExtractor();

  @Override
  public Optional<Integer> parse(String value) throws UnparsableException {
    if (StringUtils.isBlank(value)) {
      return Optional.empty();
    }
    return Optional.ofNullable(EXTRACTOR.extract(value));
  }
}
