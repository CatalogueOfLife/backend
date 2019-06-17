package org.col.parser;

import java.io.IOException;
import java.util.Optional;

import com.google.common.base.CharMatcher;
import org.col.common.text.StringUtils;
import org.gbif.utils.file.csv.CSVReader;
import org.gbif.utils.file.csv.CSVReaderFactory;

/**
 * A base parser implementation dealing with empty, invisible and punctuation values as empty results.
 */
abstract class ParserBase<T> implements Parser<T> {
  private final static CharMatcher VISIBLE = CharMatcher.invisible().negate();
  final Class valueClass;
  
  ParserBase(Class valueClass) {
    this.valueClass = valueClass;
  }
  
  @Override
  public Optional<T> parse(String value) throws UnparsableException {
    String x = normalize(value);
    if (x == null) {
      // check if we had any not invisible characters - throw Unparsable in such cases
      if (value != null && VISIBLE.matchesAnyOf(value)) {
        throw new UnparsableException(valueClass, value);
      }
      return Optional.empty();
    }
    
    T val = parseKnownValues(x);
    if (val != null) {
      return Optional.of(val);
    }
    
    throw new UnparsableException(valueClass, value);
  }
  
  protected CSVReader dictReader(String resourceFilename) throws IOException {
    return CSVReaderFactory.build(getClass().getResourceAsStream("/parser/dicts/" + resourceFilename), "UTF8", ",", null, 0);
  }
  
  /**
   * Default normalizer function that can be overridden for specific parsers.
   */
  String normalize(String x) {
    return StringUtils.digitOrAsciiLetters(x);
  }
  
  abstract T parseKnownValues(String upperCaseValue) throws UnparsableException;
}
