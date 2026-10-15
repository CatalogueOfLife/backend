package life.catalogue.matching;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.vocab.MatchingMode;
import life.catalogue.coldp.ColdpTerm;

import org.gbif.nameparser.api.Rank;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Names of uploaded matching rows, given whole or in parts.
 */
public class MatchingRowMapperTest {

  @BeforeClass
  public static void registerTerms() {
    // the server registers the ColDP terms on startup through ApiModule
    ApiModule.registerTermPackages(ColdpTerm.class);
  }

  static String name(AbstractMatchingJob.RowMapper mapper, String... row) {
    return mapper.build(row, new IssueContainer.Simple()).getName();
  }

  @Test
  public void parts() {
    var mapper = new AbstractMatchingJob.RowMapper(new String[]{"genericName", "specificEpithet", "infraspecificEpithet", "rank"});
    assertEquals("Abies alba", name(mapper, "Abies", "alba", "", ""));
    assertEquals("Abies alba subsp. apennina", name(mapper, "Abies", "alba", "apennina", "subspecies"));

    // Darwin Core terms
    mapper = new AbstractMatchingJob.RowMapper(new String[]{"dwc:genericName", "dwc:specificEpithet"});
    assertEquals("Abies alba", name(mapper, "Abies", "alba"));
  }

  @Test
  public void abbreviatedGenus() {
    var mapper = new AbstractMatchingJob.RowMapper(new String[]{"scientificName", "family", "genus"});
    assertEquals("Abies alba", name(mapper, "A. alba", "Pinaceae", "Abies"));
    assertEquals("Picea abies", name(mapper, "Picea abies", "Pinaceae", "Abies"));

    // an epithet in the name field that is also given as the epithet
    mapper = new AbstractMatchingJob.RowMapper(new String[]{"scientificName", "genus", "specificEpithet"});
    assertEquals("Abies alba", name(mapper, "alba", "Abies", "alba"));
  }

  @Test
  public void genusOnly() {
    var mapper = new AbstractMatchingJob.RowMapper(new String[]{"family", "genus"});
    assertEquals("Abies", name(mapper, "Pinaceae", "Abies"));
  }

  @Test
  public void classificationOnly() {
    var mapper = new AbstractMatchingJob.RowMapper(new String[]{"genus", "family"});
    var sn = mapper.build(new String[]{"", "Pinaceae"}, new IssueContainer.Simple());
    assertEquals("Pinaceae", sn.getName());
    assertEquals(Rank.FAMILY, sn.getRank());
    // the family is no parent of itself
    assertTrue(sn.getClassification().isEmpty());
  }

  @Test(expected = IllegalArgumentException.class)
  public void noName() {
    new AbstractMatchingJob.RowMapper(new String[]{"family", "authorship"});
  }

  @Test
  public void supportedMode() {
    assertEquals(MatchingMode.DEFAULT, AbstractMatchingJob.supportedMode(null));
    assertEquals(MatchingMode.STRICT, AbstractMatchingJob.supportedMode(MatchingMode.STRICT));
    assertThrows(IllegalArgumentException.class, () -> AbstractMatchingJob.supportedMode(MatchingMode.FUZZY));
    assertTrue(MatchingMode.DEFAULT.higherRank());
    assertFalse(MatchingMode.STRICT.higherRank());
  }
}
