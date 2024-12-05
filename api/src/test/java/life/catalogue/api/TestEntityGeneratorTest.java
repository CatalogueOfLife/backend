package life.catalogue.api;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class TestEntityGeneratorTest {

  /**
   * Jenkins builds got stuck with commons lang3 version 3.17.0 creating a verbatim test record.
   */
  @Test
  public void randomUnicodeString() {
    var v = TestEntityGenerator.createVerbatim();
    assertNotNull(v);
  }
}