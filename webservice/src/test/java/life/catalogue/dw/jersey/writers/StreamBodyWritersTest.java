package life.catalogue.dw.jersey.writers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.Test;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * The streams written by the tabular body writers carry an onClose that releases the gzip reader or db cursor the rows
 * come from. A client that goes away makes closing the response writer fail, which used to skip closing the rows.
 */
public class StreamBodyWritersTest {

  /**
   * A response stream whose client went away: writes are buffered fine, the final flush fails.
   */
  static class AbortedResponse extends OutputStream {
    @Override
    public void write(int b) {
    }

    @Override
    public void flush() throws IOException {
      throw new IOException("Broken pipe");
    }

    @Override
    public void close() throws IOException {
      throw new IOException("Broken pipe");
    }
  }

  @Test
  public void tsvClosesRows() {
    var closed = new AtomicBoolean();
    Stream<Object[]> rows = Stream.<Object[]>of(new Object[]{"a", 1}).onClose(() -> closed.set(true));
    assertThrows(IOException.class, () -> new TsvBodyWriter().writeTo(rows, null, null, null,
      MediaType.TEXT_PLAIN_TYPE, new MultivaluedHashMap<>(), new AbortedResponse()));
    assertTrue(closed.get());
  }

  @Test
  public void txtClosesRows() {
    var closed = new AtomicBoolean();
    Stream<String> rows = Stream.of("a", "b").onClose(() -> closed.set(true));
    assertThrows(IOException.class, () -> new TxtBodyWriter().writeTo(rows, null, null, null,
      MediaType.TEXT_PLAIN_TYPE, new MultivaluedHashMap<>(), new AbortedResponse()));
    assertTrue(closed.get());
  }

  @Test
  public void csvClosesRows() {
    var closed = new AtomicBoolean();
    Stream<Object[]> rows = Stream.<Object[]>of(new Object[]{"a", 1}).onClose(() -> closed.set(true));
    // univocity wraps the failing close in its own runtime exception
    assertThrows(Exception.class, () -> new CsvBodyWriter().writeTo(rows, null, null, null,
      MediaType.TEXT_PLAIN_TYPE, new MultivaluedHashMap<>(), new AbortedResponse()));
    assertTrue(closed.get());
  }
}
