package org.col.util.csl;

import org.junit.Test;

@SuppressWarnings("static-method")
public class AnyStyleWebServiceTest {

  @SuppressWarnings("unused")
  @Test(expected = IllegalStateException.class)
  public void test1() {
    AnystyleWebService svc1 = null;
    try {
      svc1 = new AnystyleWebService();
      // Can't instantiate a 2nd one
      new AnystyleWebService();
    } finally {
      svc1.stop();
    }
  }

}
