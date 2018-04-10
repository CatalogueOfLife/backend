package org.col.parser;

import org.col.parser.AnystyleWebService;
import org.junit.Test;

@SuppressWarnings("static-method")
public class AnyStyleWebServiceTest {

  // @SuppressWarnings("unused")
  // @Test(expected = IllegalStateException.class)
  // public void test1() {
  // AnystyleWebService svc1 = null;
  // try {
  // svc1 = new AnystyleWebService();
  // // Can't instantiate a 2nd one
  // new AnystyleWebService();
  // } finally {
  // svc1.stop();
  // }
  // }

  @SuppressWarnings("unused")
  @Test
  public void test2() {
    for(int i=0;i<2000000;i++) {
      AnystyleWebService svc1 = null;
      try {
        svc1 = new AnystyleWebService();
      } catch (Throwable t) {
        t.printStackTrace();
        return;
      } finally {
        svc1.stop();
      }     
    }
  }

}
