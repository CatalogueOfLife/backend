package org.col.parser;

import java.util.Optional;

import org.gbif.common.parsers.core.Parsable;
import org.gbif.common.parsers.core.ParseResult;

/**
 * @param <T> Parser value class
 * @param <G> GBIF value class
 */
abstract class GbifParserBased<T, G> implements Parser<T> {

  final Class<T> valueClass;
  private final Parsable<G> gbifParser;

  public GbifParserBased(Class<T> valueClass, Parsable<G> gbifParser) {
    this.gbifParser = gbifParser;
    this.valueClass = valueClass;
  }

  @Override
  public Optional<T> parse(String value) throws UnparsableException {
    if (org.apache.commons.lang3.StringUtils.isBlank(value)) {
      return Optional.empty();
    }

    ParseResult<G> gbifResult = gbifParser.parse(value);
    if (gbifResult.isSuccessful()) {
      Optional<T> converted = Optional.ofNullable(convertFromGbif(gbifResult.getPayload()));
      if (!converted.isPresent()) {
        throw new UnparsableException(valueClass, value);
      }
      return converted;

    } else if (gbifResult.getStatus().equals(ParseResult.STATUS.ERROR)) {
      throw new UnparsableException("Error while parsing " + valueClass.getSimpleName(), gbifResult.getError());

    } else {
      throw new UnparsableException(valueClass, value);
    }
  }

  /**
   * @param value never null!
   */
  abstract T convertFromGbif(G value) throws UnparsableException;
}
