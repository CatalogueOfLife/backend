package life.catalogue.interpreter;

import org.junit.Test;

import static org.junit.Assert.*;

public class ExtinctNameTest {

  static void assertName(String input, String name, boolean extinct, boolean hybrid) {
    var en = new ExtinctName(input);
    assertEquals(input, name, en.name);
    assertEquals(input, extinct, en.extinct);
    assertEquals(input, hybrid, en.hybrid);
  }

  @Test
  public void hybridSign() {
    assertName("× Agropogon", "Agropogon", false, true);
    assertName("×Agropogon", "Agropogon", false, true);
    assertName("x Agropogon", "Agropogon", false, true);
    assertName("X Cuprocyparis", "Cuprocyparis", false, true);
    assertName("× mitsutae", "mitsutae", false, true);
    assertName("x mitsutae", "mitsutae", false, true);
    // a sign between two epithets is a hybrid formula, not a notho marker, see #1629
    assertName("adsurgens x rhodophloia", "adsurgens x rhodophloia", false, false);
    assertName("adsurgens × rhodophloia", "adsurgens × rhodophloia", false, false);
    // a capital X glued to the name is part of it, found as a bacterial genus atom by the interpreter corpus
    assertName("XBB1006", "XBB1006", false, false);
    assertName("Xanthium", "Xanthium", false, false);
    assertName("Abies", "Abies", false, false);
  }

  @Test
  public void dagger() {
    assertName("† Abies", "Abies", true, false);
    assertName("†", null, true, false);
    assertName(" ", null, false, false);
    assertName(null, null, false, false);
  }
}
