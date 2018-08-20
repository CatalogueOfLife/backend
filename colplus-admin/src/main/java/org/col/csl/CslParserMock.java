package org.col.csl;

import java.util.Optional;

import com.google.common.base.Strings;
import org.col.api.model.CslData;
import org.col.parser.Parser;
import org.col.parser.UnparsableException;

/**
 * Mock implementation of a very simple Csl parser for tests.
 * The parser only populates the title attribute with the full citation given.
 */
@Deprecated
public class CslParserMock implements Parser<CslData> {

  @Override
  public Optional<CslData> parse(String citation) throws UnparsableException {
    if (Strings.isNullOrEmpty(citation)) {
      return Optional.empty();
    }
    CslData csl = new CslData();
    csl.setTitle(citation);
    return Optional.of(csl);
  }
}
