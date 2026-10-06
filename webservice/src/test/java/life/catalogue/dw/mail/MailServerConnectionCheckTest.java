package life.catalogue.dw.mail;

import org.junit.Test;
import org.simplejavamail.api.mailer.Mailer;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MailServerConnectionCheckTest {

  @Test
  public void testedOnceOnStart() throws Exception {
    Mailer mailer = mock(Mailer.class);
    var check = new MailServerConnectionCheck(mailer);
    assertFalse(check.check().isHealthy());
    verifyNoInteractions(mailer);

    check.start();
    assertTrue(check.check().isHealthy());
    assertTrue(check.check().isHealthy());
    // the connection is only ever tested on start, never per health check request
    verify(mailer, times(1)).testConnection(false);
    verifyNoMoreInteractions(mailer);
  }

  @Test
  public void failure() throws Exception {
    Mailer mailer = mock(Mailer.class);
    doThrow(new IllegalStateException("smtp down")).when(mailer).testConnection(false);
    var check = new MailServerConnectionCheck(mailer);
    check.start();
    var res = check.check();
    assertFalse(res.isHealthy());
    assertEquals("smtp down", res.getMessage());
  }
}
