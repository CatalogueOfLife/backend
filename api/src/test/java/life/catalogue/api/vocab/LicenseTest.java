package life.catalogue.api.vocab;

import junit.framework.TestCase;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LicenseTest {

  @Test
  public void isCompatible() throws Exception {
    assertTrue(License.isCompatible(License.CC0, License.CC_BY));
    assertTrue(License.isCompatible(License.CC0, License.CC_BY_NC));
    assertTrue(License.isCompatible(License.CC0, License.OTHER));
    assertTrue(License.isCompatible(License.CC0, License.UNSPECIFIED));

    assertTrue(License.isCompatible(License.CC_BY, License.CC_BY));
    assertTrue(License.isCompatible(License.CC_BY, License.UNSPECIFIED));

    assertFalse(License.isCompatible(License.CC_BY, License.OTHER));
    assertFalse(License.isCompatible(License.CC_BY_NC, License.CC_BY));
    assertFalse(License.isCompatible(License.CC_BY, License.CC0));
  }
}