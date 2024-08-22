package life.catalogue.parser;

import life.catalogue.common.io.TabReader;
import life.catalogue.common.text.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.google.common.base.CharMatcher;

/**
 * A base parser implementation dealing with empty, invisible and punctuation values as empty results.
 */
abstract class ParserBase<T> implements Parser<T> {
  private final static CharMatcher VISIBLE = CharMatcher.invisible().negate();
  final boolean throwUnparsableException;
  final Class valueClass;
  
  ParserBase(Class valueClass) {
    this.valueClass = valueClass;
    this.throwUnparsableException = true;
  }

  ParserBase(Class valueClass, boolean throwUnparsableException) {
    this.valueClass = valueClass;
    this.throwUnparsableException = throwUnparsableException;
  }

  /**
   * Before proper parsing this method is called to check if the normalised value
   * should be considered as empty/null without any error being thrown.
   * @param normalisedValue non null, normalised (usually upper case) value
   */
  protected boolean isEmpty(String normalisedValue) {
    return false;
  }

  @Override
  public Optional<? extends T> parse(String value) throws UnparsableException {
    String x = normalize(value);
    if (x == null) {
      // check if we had any not invisible characters - throw Unparsable in such cases
      if (throwUnparsableException && value != null && VISIBLE.matchesAnyOf(value)) {
        throw new UnparsableException(valueClass, value);
      }
      return Optional.empty();
    }
    if (isEmpty(x)) {
      return Optional.empty();
    }
    
    T val = parseKnownValues(x);
    if (val != null) {
      return Optional.of(val);

    } else if (throwUnparsableException) {
      throw new UnparsableException(valueClass, value);

    } else {
      return Optional.empty();
    }
  }

  /**
   * Parses the value and returns the parsed value or null if the value was empty or the parser fails.
   * Will never throw UnparsableException
   */
  public T parseOrNull(String value) {
    return SafeParser.parse(this, value).orNull();
  }

  protected TabReader dictReader(String resourceFilename) throws IOException {
    return TabReader.csv(getClass().getResourceAsStream("/parser/dicts/" + resourceFilename), StandardCharsets.UTF_8, 0, 2);
  }
  
  /**
   * Default normalizer function that can be overridden for specific parsers.
   */
  String normalize(String x) {
    return StringUtils.digitOrAsciiLetters(x);
  }
  
  abstract T parseKnownValues(String upperCaseValue) throws UnparsableException;
}
