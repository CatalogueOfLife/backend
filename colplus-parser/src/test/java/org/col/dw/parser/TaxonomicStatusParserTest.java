package org.col.dw.parser;

import com.google.common.collect.Lists;
import org.col.dw.api.vocab.TaxonomicStatus;
import org.junit.Test;

import java.util.List;

/**
 *
 */
public class TaxonomicStatusParserTest extends ParserTestBase<TaxonomicStatus> {

  public TaxonomicStatusParserTest() {
    super(TaxonomicStatusParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(TaxonomicStatus.ACCEPTED, "valid");
  }

  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter");
  }
}