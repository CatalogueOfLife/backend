package life.catalogue.dw.metrics;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HttpClientBuilderTest {

  @Test
  public void simpleName() {
    assertEquals("COLServer", HttpClientBuilder.simpleName("COLServer/da74866_2021-11-05"));
    assertEquals("COLServer", HttpClientBuilder.simpleName("COLServer/da74866 2021-11-05"));
  }
}