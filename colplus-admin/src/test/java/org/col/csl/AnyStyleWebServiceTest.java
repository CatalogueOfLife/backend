package org.col.csl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.io.IOException;

import org.junit.Ignore;
import org.junit.Test;

@SuppressWarnings("static-method")
public class AnyStyleWebServiceTest {

  @Test(expected = IllegalStateException.class)
  @Ignore
  public void test1() throws InterruptedException, IOException {
    AnystyleWebService svc1 = new AnystyleWebService();
    try {
      svc1.start();
      assertTrue(AnystyleWebService.isRunning());
      assertTrue(AnystyleWebService.isListening());
      svc1.start();
    } finally {
      svc1.stop();
    }
    assertFalse(AnystyleWebService.isRunning());
    assertFalse(AnystyleWebService.isListening());
  }

  @Test
  @Ignore
  public void test2() throws IOException, InterruptedException {
    AnystyleWebService svc1 = new AnystyleWebService();
    for (int i = 0; i < 10; i++) {
      try {
        svc1.start();
      } finally {
        svc1.stop();
      }
    }
  }
  
}
