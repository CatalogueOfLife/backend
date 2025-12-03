package life.catalogue.dw.mail;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.simplejavamail.api.mailer.Mailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.health.HealthCheck;

public final class MailServerConnectionCheck extends HealthCheck {
  private static final Logger LOG = LoggerFactory.getLogger(MailServerConnectionCheck.class);
  private final Mailer mailer;

  public MailServerConnectionCheck(final Mailer mailer) {
    this.mailer = mailer;
  }

  @Override
  protected Result check() throws Exception {
    try {
      var f = mailer.testConnection(true);
      var res = f.get(2, TimeUnit.SECONDS);
    } catch (ExecutionException | TimeoutException e) {
      LOG.info( "Mail server connection check unsuccessful", e);
      return Result.unhealthy(e);
    }
    return Result.healthy();
  }
}