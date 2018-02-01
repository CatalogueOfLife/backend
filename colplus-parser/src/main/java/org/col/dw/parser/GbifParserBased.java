package org.col.dw.parser;

import org.gbif.common.parsers.core.Parsable;
import org.gbif.common.parsers.core.ParseResult;

/**
 *
 */
abstract class GbifParserBased<T, G> extends ParserBase<T> {

  private final Parsable<G> gbifParser;

  public GbifParserBased(Class<T> valueClass, Parsable<G> gbifParser) {
    super(valueClass);
    this.gbifParser = gbifParser;
  }

  /**
   * @param value never null!
   */
  abstract T convertFromGbif(G value);

  @Override
  T parseKnownValues(String upperCaseValue) throws UnparsableException {
    ParseResult<G> gbifResult = gbifParser.parse(upperCaseValue);
    if (gbifResult.isSuccessful()) {
      return convertFromGbif(gbifResult.getPayload());

    } else if (gbifResult.getStatus().equals(ParseResult.STATUS.ERROR)) {
      throw new UnparsableException("Error while parsing " + valueClass.getSimpleName(), gbifResult.getError());

    } else {
      return null;
    }
  }
}
