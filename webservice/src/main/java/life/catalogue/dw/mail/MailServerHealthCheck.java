package life.catalogue.dw.mail;

import com.codahale.metrics.health.HealthCheck;
import org.simplejavamail.api.mailer.Mailer;

public final class MailServerHealthCheck extends HealthCheck {
  private final Mailer mailer;

  public MailServerHealthCheck(final Mailer mailer) {
    this.mailer = mailer;
  }

  @Override
  protected Result check() throws Exception {
    if (mailer.getSession().getTransport().isConnected()) {
      return Result.healthy();
    } else {
      return Result.unhealthy("mailer is not connected");
    }
  }
}