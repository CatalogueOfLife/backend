package org.col.parser;

import com.google.common.collect.Lists;
import org.col.api.vocab.TaxonomicStatus;
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
    assertParse(TaxonomicStatus.ACCEPTED, "Valid");
    assertParse(TaxonomicStatus.ACCEPTED, "VALID");
  }

  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter");
  }
}