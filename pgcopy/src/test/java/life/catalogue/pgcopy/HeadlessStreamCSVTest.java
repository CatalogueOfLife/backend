package life.catalogue.pgcopy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HeadlessStreamCSVTest {

  /**
   * InputStream.read() returns a byte as 0 to 255. A negative value for a non ASCII byte breaks readers
   * that take anything negative as the end of the stream, and a 0xFF byte reads as the end of the stream.
   */
  @Test
  public void readUnsignedBytes() throws Exception {
    String csv = "id,name\n1,Müller\n2,Ærø\n";
    try (var in = new PgCopyUtils.HeadlessStreamCSV(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), Map.of(), List.of())) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      int b;
      while ((b = in.read()) != -1) {
        assertTrue("byte out of range: " + b, b >= 0 && b <= 255);
        out.write(b);
      }
      assertEquals("1,Müller\n2,Ærø\n", out.toString(StandardCharsets.UTF_8));
    }
  }
}
