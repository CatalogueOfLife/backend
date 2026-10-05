package life.catalogue.dw.mail;

import org.simplejavamail.api.mailer.Mailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.health.HealthCheck;

import io.dropwizard.lifecycle.Managed;

/**
 * Tests the SMTP connection once when the app starts and reports that result from then on,
 * which verifies the mail configuration. It used to open a connection on every /healthcheck request,
 * which every open ChecklistBank tab polls, and a slow connect then turned the entire endpoint into a 500.
 */
public final class MailServerConnectionCheck extends HealthCheck implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(MailServerConnectionCheck.class);
  private final Mailer mailer;
  private volatile Result result = Result.unhealthy("Mail server connection not tested yet");

  public MailServerConnectionCheck(final Mailer mailer) {
    this.mailer = mailer;
  }

  @Override
  public void start() throws Exception {
    try {
      // blocking, so bound by the mailer session timeout
      mailer.testConnection(false);
      LOG.info("Mail server connection successful");
      result = Result.healthy();
    } catch (RuntimeException e) {
      LOG.error("Mail server connection unsuccessful", e);
      result = Result.unhealthy(e);
    }
  }

  @Override
  protected Result check() throws Exception {
    return result;
  }
}