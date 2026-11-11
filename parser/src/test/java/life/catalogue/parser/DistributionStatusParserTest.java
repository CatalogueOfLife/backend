package life.catalogue.parser;

import life.catalogue.api.vocab.DistributionStatus;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DistributionStatusParserTest {

  /**
   * A presence says nothing about how a taxon got there. It used to parse as native, which made every
   * introduced GRIIS species with an unparsable establishment means native.
   */
  @Test
  public void presentIsNotNative() throws Exception {
    assertFalse(SafeParser.parse(DistributionStatusParser.PARSER, "present").isPresent());
    assertEquals(DistributionStatus.NATIVE, DistributionStatusParser.PARSER.parse("endemic").get());
  }

  @Test
  public void isAbsent() {
    for (String x : new String[]{"absent", "Absent", " ABSENT ", "excluded", "Excluded", "not present", "Not_Present",
      "not-present", "notPresent", "non present", "not  found", "not detected", "not recorded", "absence"}) {
      assertTrue(x, DistributionStatusParser.isAbsent(x));
    }
    for (String x : new String[]{null, "", " ", "present", "common", "rare", "irregular", "doubtful", "native", "endemic",
      "extinct", "extirpated", "absently", "0", "no"}) {
      assertFalse(x, DistributionStatusParser.isAbsent(x));
    }
  }
}
