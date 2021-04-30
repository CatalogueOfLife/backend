package life.catalogue.dw.mail;

import com.codahale.metrics.health.HealthCheck;

import java.net.Socket;

public class SocketHealthCheck extends HealthCheck {
  private final MailConfig cfg;

  public SocketHealthCheck(final MailConfig cfg) {
    this.cfg = cfg;
  }

  @Override
  protected Result check() throws Exception {
    try (final Socket socket = new Socket(cfg.host, cfg.port)) {
      return Result.healthy();
    } catch (Exception e) {
      return Result.unhealthy("Could not connect to %s:%s", cfg.host, cfg.port);
    }
  }
}