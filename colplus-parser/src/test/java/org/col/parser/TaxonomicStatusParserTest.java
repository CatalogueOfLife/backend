package org.col.parser;

import java.util.List;

import com.google.common.collect.Lists;
import org.col.api.vocab.TaxonomicStatus;
import org.junit.Test;

import static org.col.api.vocab.TaxonomicStatus.*;

/**
 *
 */
public class TaxonomicStatusParserTest extends EnumNoteParserTestBase<TaxonomicStatus> {

  public TaxonomicStatusParserTest() {
    super(TaxonomicStatusParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(ACCEPTED, "valid");
    assertParse(ACCEPTED, "Valid");
    assertParse(ACCEPTED, "VALID");
    assertParse(ACCEPTED, "accepted");

    assertParse(PROVISIONALLY_ACCEPTED,"provisional");

    assertParse(SYNONYM, "synonym");
    assertParse(SYNONYM, "juniorsynonym");
    assertParse(SYNONYM, "unaccepted!");
    assertParse(SYNONYM, "sinônimo");

    assertParse(AMBIGUOUS_SYNONYM, " ambiguoussynonym");
    assertParse(AMBIGUOUS_SYNONYM, "Pro-Parte");

    assertParse(SYNONYM, TaxonomicStatusParser.HOMOTYPIC_NOTE, "homotypicsynonym");
  }

  private void assertNote() {

  }

  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter");
  }
}