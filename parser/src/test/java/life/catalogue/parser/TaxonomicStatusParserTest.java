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
    assertParse(PROVISIONALLY_ACCEPTED,"To determine");
    assertParse(PROVISIONALLY_ACCEPTED,"To be determined");
    assertParse(PROVISIONALLY_ACCEPTED,"To be done");
    assertParse(PROVISIONALLY_ACCEPTED,"tbd");

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
    assertParse(BARE_NAME, "unassessed");
    assertParse(BARE_NAME, "nomen dubium");
    assertParse(BARE_NAME, "taxon inquirendum");
  }

  /**
   * All 26 distinct dwc:taxonomicStatus values published by WoRMS, see
   * https://github.com/CatalogueOfLife/backend/issues/1571
   * WoRMS squeezes nomenclatural statements into this column, so several values are
   * only synonyms in the CoL sense of the word.
   */
  @Test
  public void worms() throws Exception {
    assertParse(ACCEPTED, "accepted");
    assertParse(SYNONYM, "unaccepted");
    assertParse(BARE_NAME, "unassessed");
    assertParse(SYNONYM, TaxonomicStatusParser.HOMOTYPIC_NOTE, "superseded combination");
    assertParse(SYNONYM, "junior subjective synonym");
    assertParse(SYNONYM, "alternative representation");
    assertParse(BARE_NAME, "taxon inquirendum");
    assertParse(SYNONYM, TaxonomicStatusParser.HOMOTYPIC_NOTE, "superseded rank");
    assertParse(SYNONYM, TaxonomicStatusParser.HOMOTYPIC_NOTE, "misspelling - incorrect subsequent spelling");
    assertParse(BARE_NAME, "nomen dubium");
    assertParse(SYNONYM, "nomen nudum");
    assertParse(SYNONYM, "junior homonym");
    assertParse(SYNONYM, TaxonomicStatusParser.HOMOTYPIC_NOTE, "junior objective synonym");
    assertParse(PROVISIONALLY_ACCEPTED, "unreplaced junior homonym");
    assertParse(BARE_NAME, "uncertain");
    assertParse(BARE_NAME, "unavailable name");
    assertParse(SYNONYM, TaxonomicStatusParser.HOMOTYPIC_NOTE, "incorrect grammatical agreement of specific epithet");
    assertParse(SYNONYM, TaxonomicStatusParser.HOMOTYPIC_NOTE, "misspelling - incorrect original spelling");
    assertParse(PROVISIONALLY_ACCEPTED, "temporary name");
    assertParse(SYNONYM, TaxonomicStatusParser.HOMOTYPIC_NOTE, "unjustified emendation");
    assertParse(MISAPPLIED, "misapplication");
    assertParse(BARE_NAME, "interim unpublished");
    assertParse(SYNONYM, "nomen oblitum");
    assertParse(ACCEPTED, "nomen novum");
    assertParse(SYNONYM, "nomen rejiciendum");
    assertParse(ACCEPTED, "nomen protectum");
  }

  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter");
  }
}