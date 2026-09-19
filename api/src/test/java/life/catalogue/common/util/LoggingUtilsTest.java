package life.catalogue.common.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LoggingUtilsTest {

  /**
   * Release logs are published with every release, and post release actions log the URL they call.
   */
  @Test
  public void redactCredentials() {
    assertEquals("POST https://builds.example.org/job/col-portal/buildWithParameters?ENV=preview&token=REDACTED&cause=base+release -> 201",
      LoggingUtils.redactCredentials("POST https://builds.example.org/job/col-portal/buildWithParameters?ENV=preview&token=s3cr3tT0ken&cause=base+release -> 201"));
    assertEquals("https://example.org/?api_key=REDACTED", LoggingUtils.redactCredentials("https://example.org/?api_key=abc123"));
    assertEquals("https://example.org/?PASSWORD=REDACTED&x=1", LoggingUtils.redactCredentials("https://example.org/?PASSWORD=pw&x=1"));
    assertEquals("Authorization: Bearer REDACTED", LoggingUtils.redactCredentials("Authorization: Bearer eyJhbGciOiJIUzUxMiJ9.e30.sig"));
    // parameters that merely end in key are identifiers, not credentials
    assertEquals("dataset/3/sector?sectorKey=5&datasetKey=3", LoggingUtils.redactCredentials("dataset/3/sector?sectorKey=5&datasetKey=3"));
    assertNull(LoggingUtils.redactCredentials(null));
  }
}
