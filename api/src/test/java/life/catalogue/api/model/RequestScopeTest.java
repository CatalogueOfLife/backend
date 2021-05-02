package life.catalogue.api.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class RequestScopeTest {

  @Test
  public void testEquals() {
    RequestScope r1 = new RequestScope();
    RequestScope r2 = new RequestScope();
    assertEquals(r1, r2);

    r1.setAll(true);
    assertNotEquals(r1, r2);

    r2.setAll(true);
    assertEquals(r1, r2);

    r1.setAll(false);
    r2.setAll(false);
    r2.setDatasetKey(13);
    assertNotEquals(r1, r2);

    r1.setDatasetKey(15);
    assertNotEquals(r1, r2);

    r2.setDatasetKey(15);
    assertEquals(r1, r2);
  }
}