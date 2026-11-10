package life.catalogue.matching.authorship;

import life.catalogue.matching.Equality;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 */
public class EqualityTest {

  @Test
  public void testAnd() throws Exception {
    assertEquals(Equality.EQUAL, Equality.EQUAL.and(Equality.EQUAL));
    assertEquals(Equality.EQUAL, Equality.EQUAL.and(Equality.UNKNOWN));
    assertEquals(Equality.EQUAL, Equality.UNKNOWN.and(Equality.EQUAL));

    assertEquals(Equality.UNKNOWN, Equality.UNKNOWN.and(Equality.UNKNOWN));

    assertEquals(Equality.DIFFERENT, Equality.DIFFERENT.and(Equality.DIFFERENT));
    assertEquals(Equality.DIFFERENT, Equality.UNKNOWN.and(Equality.DIFFERENT));
    assertEquals(Equality.DIFFERENT, Equality.DIFFERENT.and(Equality.UNKNOWN));
    assertEquals(Equality.DIFFERENT, Equality.EQUAL.and(Equality.DIFFERENT));
    assertEquals(Equality.DIFFERENT, Equality.DIFFERENT.and(Equality.EQUAL));

    // the more different answer wins
    assertEquals(Equality.PROBABLY_EQUAL, Equality.EQUAL.and(Equality.PROBABLY_EQUAL));
    assertEquals(Equality.PROBABLY_EQUAL, Equality.PROBABLY_EQUAL.and(Equality.UNKNOWN));
    assertEquals(Equality.PROBABLY_DIFFERENT, Equality.PROBABLY_EQUAL.and(Equality.PROBABLY_DIFFERENT));
    assertEquals(Equality.PROBABLY_DIFFERENT, Equality.UNKNOWN.and(Equality.PROBABLY_DIFFERENT));
    assertEquals(Equality.DIFFERENT, Equality.PROBABLY_DIFFERENT.and(Equality.DIFFERENT));
    assertEquals(Equality.DIFFERENT, Equality.DIFFERENT.and(Equality.PROBABLY_EQUAL));
  }

  @Test
  public void certainAndProbable() throws Exception {
    assertTrue(Equality.EQUAL.isEqual());
    assertTrue(Equality.PROBABLY_EQUAL.isEqual());
    assertFalse(Equality.UNKNOWN.isEqual());
    assertFalse(Equality.PROBABLY_DIFFERENT.isEqual());

    assertTrue(Equality.DIFFERENT.isDifferent());
    assertTrue(Equality.PROBABLY_DIFFERENT.isDifferent());
    assertFalse(Equality.UNKNOWN.isDifferent());
    assertFalse(Equality.PROBABLY_EQUAL.isDifferent());
  }
}