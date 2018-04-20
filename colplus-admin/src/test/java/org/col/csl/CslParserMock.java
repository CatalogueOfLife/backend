package org.col.csl;

import com.google.common.base.Strings;
import org.col.api.model.CslItemData;
import org.col.parser.Parser;
import org.col.parser.UnparsableException;

import java.util.Optional;

/**
 * Mock implementation of a very simple Csl parser for tests.
 * The parser only populates the title attribute with the full citation given.
 */
public class CslParserMock implements Parser<CslItemData> {

  @Override
  public Optional<CslItemData> parse(String citation) throws UnparsableException {
    if (Strings.isNullOrEmpty(citation)) {
      return Optional.empty();
    }
    CslItemData csl = new CslItemData();
    csl.setTitle(citation);
    return Optional.of(csl);
  }
}
