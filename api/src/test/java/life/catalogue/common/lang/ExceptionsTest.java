package life.catalogue.common.lang;

import life.catalogue.api.exception.NotFoundException;

import java.io.IOException;

import org.junit.Test;

import static org.junit.Assert.*;

public class ExceptionsTest {

  @Test
  public void isRootCause() {
    Exception t = new RuntimeException(new InterruptedException("Hallo"));
    assertTrue(Exceptions.isRootCause(t, InterruptedException.class));
    assertFalse(Exceptions.isRootCause(t, IllegalArgumentException.class));

    t = new InterruptedException("Hallo");
    assertFalse(Exceptions.isRootCause(t, InterruptedException.class));
    assertFalse(Exceptions.isRootCause(t, IllegalArgumentException.class));
  }

  @Test
  public void containsInstanceOf() {
    Exception t = new RuntimeException(new InterruptedException("Hallo"));
    assertTrue(Exceptions.containsInstanceOf(t, InterruptedException.class));
    assertFalse(Exceptions.containsInstanceOf(t, IllegalArgumentException.class));

    t = new InterruptedException("Hallo");
    assertTrue(Exceptions.containsInstanceOf(t, InterruptedException.class));
    assertFalse(Exceptions.containsInstanceOf(t, IllegalArgumentException.class));

    t = new RuntimeException(new IllegalStateException(new IOException(new InterruptedException("Hallo"))));
    assertTrue(Exceptions.containsInstanceOf(t, InterruptedException.class));
    assertTrue(Exceptions.containsInstanceOf(t, IllegalStateException.class));
    assertTrue(Exceptions.containsInstanceOf(t, RuntimeException.class));
    assertTrue(Exceptions.containsInstanceOf(t, IOException.class));
    assertTrue(Exceptions.containsInstanceOf(t, Exception.class));
    assertTrue(Exceptions.containsInstanceOf(t, RuntimeException.class));
    assertFalse(Exceptions.containsInstanceOf(t, IllegalArgumentException.class));
    assertFalse(Exceptions.containsInstanceOf(t, NotFoundException.class));
  }

  @Test
  public void simpleLog() {
    Exception t = new InterruptedException("Hallo");
    assertEquals("InterruptedException: Hallo", Exceptions.simpleLogWithCauses(t));

    t = new RuntimeException();
    assertEquals("RuntimeException", Exceptions.simpleLogWithCauses(t));

    t = new RuntimeException(new InterruptedException("Hallo"));
    assertEquals("InterruptedException: Hallo", Exceptions.simpleLogWithCauses(t));

    t = new RuntimeException(new IllegalStateException("No State to live in", new IOException(new InterruptedException("Hallo"))));
    assertEquals("IllegalStateException: No State to live in; IOException; InterruptedException: Hallo", Exceptions.simpleLogWithCauses(t));

    t = new RuntimeException(new IllegalStateException(new IOException(new InterruptedException("Hallo"))));
    assertEquals("IllegalStateException; IOException; InterruptedException: Hallo", Exceptions.simpleLogWithCauses(t));
  }
}