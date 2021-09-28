package life.catalogue.dw.metrics;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GangliaReporterTest {

  @Test
  public void escapeSlashes() throws Exception {
    assertEquals("COLServer_d8760c3", GangliaReporter.escapeSlashes("COLServer/d8760c3"));
  }
}