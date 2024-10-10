package life.catalogue.api;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class TestEntityGeneratorTest {

  @Test
  public void randomUnicodeString() {
    var v = TestEntityGenerator.createVerbatim();
    assertNotNull(v);
  }
}