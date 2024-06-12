package life.catalogue.dw.mail;


import life.catalogue.concurrent.EmailNotification;
import life.catalogue.config.MailConfig;

import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.mailer.MailerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.core.ConfiguredBundle;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.lifecycle.Managed;

/**
 * Bundle that sets up a SMTP mailer.
 * If no host is configured no mailer is created.
 */
public class MailBundle implements ConfiguredBundle<MailBundleConfig> {
  
  private static final Logger LOG = LoggerFactory.getLogger(MailBundle.class);
  private Mailer mailer;
  private EmailNotification emailNotification;

  @Override
  public void run(MailBundleConfig config, Environment env) throws Exception {
    final MailConfig cfg = config.getMailConfig();

    if (cfg.host != null) {
      LOG.info("Configuring mail server {}:{}", cfg.host, cfg.port);
      if (cfg.block) {
        LOG.warn("Mail server configured to block while sending");
      }
      mailer = MailerBuilder
        .withSMTPServer(cfg.host, cfg.port, cfg.username, cfg.password)
        .withTransportStrategy(cfg.transport)
        .withDebugLogging(cfg.block)
        .withThreadPoolSize(cfg.threads)
        .buildMailer();
      emailNotification = new EmailNotification(mailer, cfg);
      // health tests
      if (env != null) {
        env.healthChecks().register("mail-connection", new MailServerConnectionCheck(mailer));
        env.lifecycle().manage(new ManagedMailer(mailer));
      }
    } else {
      LOG.warn("No mail server configured");
    }
  }

  static class ManagedMailer implements Managed  {
    private final Mailer mailer;

    public ManagedMailer(Mailer mailer) {
      this.mailer = mailer;
    }

    @Override
    public void start() throws Exception {
    }

    @Override
    public void stop() throws Exception {
      mailer.shutdownConnectionPool();
    }
  }

  public Mailer getMailer() {
    return mailer;
  }

  public EmailNotification getEmailNotification() {
    return emailNotification;
  }

  @Override
  public void initialize(Bootstrap<?> bootstrap) {
    //Do nothing
  }
  
}