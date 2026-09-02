package life.catalogue.parser;

import life.catalogue.api.vocab.NomStatus;
import life.catalogue.api.vocab.TaxonomicStatus;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

import static life.catalogue.api.vocab.TaxonomicStatus.*;
import static life.catalogue.parser.TaxonomicStatusParser.HOMOTYPIC_NOTE;

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

    assertParse(SYNONYM, HOMOTYPIC_NOTE, "homotypicsynonym");
  
    assertParse(ACCEPTED, "1");
    assertParse(AMBIGUOUS_SYNONYM, "2");
    assertParse(MISAPPLIED, "3");
    assertParse(PROVISIONALLY_ACCEPTED,"4");
    assertParse(SYNONYM, "5");

    assertParse(BARE_NAME, "bare");
    assertParse(BARE_NAME, "bare name");
    assertParse(BARE_NAME, "unplaced");
    assertParse(BARE_NAME, "unassessed");
    assertParse(BARE_NAME, null, NomStatus.DOUBTFUL, "nomen dubium");
    assertParse(BARE_NAME, null, NomStatus.DOUBTFUL, "taxon inquirendum");
  }

  /**
   * All 26 distinct dwc:taxonomicStatus values published by WoRMS, see
   * https://github.com/CatalogueOfLife/backend/issues/1571
   * WoRMS squeezes nomenclatural statements into this column, so several values also declare the
   * NomStatus they imply. The 4 that do NOT are the point of the exercise: "valid", "invalid",
   * "doubtful" and "uncertain" are verdicts about the taxon, not about the name.
   */
  @Test
  public void worms() throws Exception {
    assertParse(ACCEPTED, "accepted");
    assertParse(SYNONYM, "unaccepted");
    assertParse(BARE_NAME, "unassessed");
    assertParse(SYNONYM, HOMOTYPIC_NOTE, "superseded combination");
    assertParse(SYNONYM, "junior subjective synonym");
    assertParse(SYNONYM, "alternative representation");
    assertParse(BARE_NAME, null, NomStatus.DOUBTFUL, "taxon inquirendum");
    assertParse(SYNONYM, HOMOTYPIC_NOTE, "superseded rank");
    assertParse(SYNONYM, HOMOTYPIC_NOTE, NomStatus.NOT_ESTABLISHED, "misspelling - incorrect subsequent spelling");
    assertParse(BARE_NAME, null, NomStatus.DOUBTFUL, "nomen dubium");
    assertParse(SYNONYM, null, NomStatus.NOT_ESTABLISHED, "nomen nudum");
    assertParse(SYNONYM, null, NomStatus.UNACCEPTABLE, "junior homonym");
    assertParse(SYNONYM, HOMOTYPIC_NOTE, "junior objective synonym");
    assertParse(PROVISIONALLY_ACCEPTED, null, NomStatus.UNACCEPTABLE, "unreplaced junior homonym");
    assertParse(BARE_NAME, "uncertain");
    assertParse(BARE_NAME, null, NomStatus.NOT_ESTABLISHED, "unavailable name");
    assertParse(SYNONYM, HOMOTYPIC_NOTE, "incorrect grammatical agreement of specific epithet");
    assertParse(SYNONYM, HOMOTYPIC_NOTE, NomStatus.NOT_ESTABLISHED, "misspelling - incorrect original spelling");
    assertParse(PROVISIONALLY_ACCEPTED, "temporary name");
    assertParse(SYNONYM, HOMOTYPIC_NOTE, "unjustified emendation");
    assertParse(MISAPPLIED, "misapplication");
    assertParse(BARE_NAME, null, NomStatus.MANUSCRIPT, "interim unpublished");
    assertParse(SYNONYM, null, NomStatus.REJECTED, "nomen oblitum");
    assertParse(ACCEPTED, null, NomStatus.ACCEPTABLE, "nomen novum");
    assertParse(SYNONYM, null, NomStatus.REJECTED, "nomen rejiciendum");
    assertParse(ACCEPTED, null, NomStatus.CONSERVED, "nomen protectum");
  }

  /**
   * A taxonomic verdict must never declare a nomenclatural status, however much its wording looks
   * like one. In zoology a "valid" name is a statement about the taxon, and stamping ACCEPTABLE on
   * it would put a nomenclatural claim on nearly every accepted name of every zoological source.
   */
  @Test
  public void noNomStatusForTaxonomicVerdicts() throws Exception {
    assertParse(ACCEPTED, "valid");
    assertParse(SYNONYM, "invalid");
    assertParse(PROVISIONALLY_ACCEPTED, "doubtful");
    assertParse(BARE_NAME, "uncertain");
    assertParse(SYNONYM, "unaccepted");
    assertParse(BARE_NAME, "unassessed");
  }

  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter");
  }
}