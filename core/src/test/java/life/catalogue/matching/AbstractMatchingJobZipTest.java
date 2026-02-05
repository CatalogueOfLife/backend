package life.catalogue.matching;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.TabularFormat;
import life.catalogue.config.MatchingConfig;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit test to verify AbstractMatchingJob produces valid ZIP files
 * without requiring database setup.
 */
public class AbstractMatchingJobZipTest {

  @Test
  public void testMatchToOutProducesValidZip() throws Exception {
    // Create a minimal matching request
    MatchingRequest req = new MatchingRequest();
    req.setDatasetKey(1);
    req.setSourceDatasetKey(1);
    req.setFormat(TabularFormat.TSV);

    // Create a test job instance with mocked dependencies
    TestMatchingJob job = new TestMatchingJob(req);

    // Write to a byte array output stream
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    job.matchToOut(baos);

    // Verify the output is a valid ZIP
    byte[] zipBytes = baos.toByteArray();
    assertTrue("ZIP output should not be empty", zipBytes.length > 0);

    // Try to read as ZIP - this will fail if ZIP structure is invalid
    try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
      ZipEntry entry = zis.getNextEntry();
      assertNotNull("ZIP should contain at least one entry", entry);
      assertTrue("Entry should be a TSV file", entry.getName().endsWith(".tsv"));

      // Verify we can read the content
      byte[] buffer = new byte[1024];
      int read = zis.read(buffer);
      assertTrue("Should be able to read ZIP entry content", read > 0);

      // Verify there's no more entries (we only write one)
      assertNull("Should only have one entry", zis.getNextEntry());
    }
  }

  /**
   * Test implementation of AbstractMatchingJob that provides minimal implementation
   */
  private static class TestMatchingJob extends AbstractMatchingJob {
    public TestMatchingJob(MatchingRequest req) throws IOException {
      super(req, 1, new Dataset(), List.of(),
        mock(UsageMatcher.class),
        new MatchingConfig(),
        mock(life.catalogue.matching.nidx.NameIndex.class));
    }

    @Override
    public org.apache.ibatis.session.SqlSession openSession() {
      return null;
    }

    @Override
    public void runWithLock() throws Exception {
      // Not needed for this test
    }
  }
}
