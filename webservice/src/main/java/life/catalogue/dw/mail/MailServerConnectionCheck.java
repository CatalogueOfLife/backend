package life.catalogue.dw.mail;

import org.simplejavamail.api.mailer.Mailer;

import com.codahale.metrics.health.HealthCheck;

public final class MailServerConnectionCheck extends HealthCheck {
  private final Mailer mailer;

  public MailServerConnectionCheck(final Mailer mailer) {
    this.mailer = mailer;
  }

  @Override
  protected Result check() throws Exception {
    mailer.testConnection();
    return Result.healthy();
  }
}