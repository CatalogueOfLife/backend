package life.catalogue.matching.person;

import life.catalogue.common.tax.AuthorshipNormalizer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PersonKeysTest {

  @Test
  public void keyOfAForm() {
    assertEquals("sw", PersonKeys.key("Sw."));
  }

  /** nothing is indexed or looked up under an empty key */
  @Test
  public void punctuationIsNoKey() {
    assertNull(PersonKeys.key(null));
    assertNull(PersonKeys.key(""));
    assertNull(PersonKeys.key(" "));
    assertNull(PersonKeys.key("."));
  }

  /** normalizing is not idempotent: a key normalized once more is still the same key */
  @Test
  public void fixpoint() {
    for (String name : new String[]{"Đinh", "McQueen", "Saeed"}) {
      assertEquals(name, PersonKeys.key(name), PersonKeys.key(AuthorshipNormalizer.normalize(name)));
    }
  }
}
