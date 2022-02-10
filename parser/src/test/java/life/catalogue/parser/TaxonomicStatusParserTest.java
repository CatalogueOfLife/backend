package life.catalogue.parser;

import life.catalogue.api.vocab.TaxonomicStatus;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

import static life.catalogue.api.vocab.TaxonomicStatus.*;

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
  
    assertParse(ACCEPTED, "1");
    assertParse(AMBIGUOUS_SYNONYM, "2");
    assertParse(MISAPPLIED, "3");
    assertParse(PROVISIONALLY_ACCEPTED,"4");
    assertParse(SYNONYM, "5");

    assertParse(BARE_NAME, "bare");
    assertParse(BARE_NAME, "bare name");
    assertParse(BARE_NAME, "unplaced");
  }

  private void assertNote() {

  }

  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter");
  }
}