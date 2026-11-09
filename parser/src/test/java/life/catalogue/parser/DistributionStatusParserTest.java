package life.catalogue.parser;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DistributionStatusParserTest {

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
