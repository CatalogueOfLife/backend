package life.catalogue.concurrent;

import life.catalogue.api.model.User;
import life.catalogue.common.lang.Exceptions;
import life.catalogue.config.MailConfig;
import life.catalogue.metadata.FmUtil;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nullable;

import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.email.EmailBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import freemarker.template.TemplateException;

public class EmailNotification {
  private static final Logger LOG = LoggerFactory.getLogger(EmailNotification.class);

  private final Mailer mailer;
  private final MailConfig cfg;

  /**
   * @param mailer if null no mails will be sent
   */
  public EmailNotification(@Nullable Mailer mailer,  MailConfig cfg) {
    this.mailer = mailer;
    this.cfg = cfg;
  }

  /**
   * Sends an email to the user that created the job.
   * It differs between a regularly finished or canceled and error status of a job.
   */
  <T extends BackgroundJob> void sendFinalEmail(final T job) {
    if (job.getEmailTemplatePrefix() == null) return; // we dont want to send emails for these kind of jobs!
    final var user = job.getUser();

    final var status = job.getStatus();
    if (!status.isDone()) {
      LOG.info("Do not notify users about {} {} {}", job.getStatus(), job.getJobName(), job.getKey());
      return;
    }

    String text = null;
    try {
      text = buildEmailText(job);
      if (mailer != null) {
        Email mail = EmailBuilder.startingBlank()
          .to(user.getName(), user.getEmail())
          .from(cfg.fromName, cfg.from)
          .withReplyTo(cfg.replyTo)
          .bccAddresses(cfg.bcc)
          .withSubject(String.format("%s %s %s %s", cfg.subjectPrefix, job.getJobName(), job.getKey(), job.getStatus().name().toLowerCase()))
          .withPlainText(text)
          .buildEmail();

        var asyncResp = mailer.sendMail(mail, true).thenAccept((resp) -> {
          LOG.info("Successfully sent mail for {} {} to {}", job.getJobName(), job.getKey(), user.getEmail());
        }).exceptionally((e) -> {
          LOG.error("Error sending mail for {} {} to {}", job.getJobName(), job.getKey(), user.getEmail(), e);
          return null;
        });
        if (cfg.block && asyncResp != null) {
          asyncResp.get(); // blocks
        }
        LOG.info("Sent email notification for {} {} to user [{}] {} <{}>", job.getJobName(), job.getKey(), user.getKey(), user.getName(), user.getEmail());

      } else {
        LOG.warn("No mailer configured to sent email notification for {} {} to user [{}] {} <{}>", job.getJobName(), job.getKey(), user.getKey(), user.getName(), user.getEmail());
        LOG.debug(text);
      }

    } catch (IOException | TemplateException | ExecutionException | InterruptedException | RuntimeException e) {
      LOG.error("Error sending mail for {} {}", job.getJobName(), job.getKey(), e);
      if (text != null) {
        LOG.info(text);
      }
      if (cfg != null && cfg.block) {
        throw Exceptions.asRuntimeException(e);
      }
    }
  }

  @VisibleForTesting
  String buildEmailText(BackgroundJob job) throws TemplateException, IOException {
    final EmailData data = job.getEmailData(cfg);
    final String template = "email/" + job.getEmailTemplatePrefix() + "-" + job.getStatus().name().toLowerCase() + ".ftl";
    return FmUtil.render(data, template);
  }

  void sendErrorMail(BackgroundJob job){
    if (mailer != null && cfg.onErrorTo != null && cfg.from != null) {
      StringWriter sw = new StringWriter();
      sw.write(job.getJobName() + " job "+job.getKey());
      sw.write(" has failed with an exception");
      if (job.getError() != null) {
        sw.write(" " + job.getError().getClass().getSimpleName()+":\n\n");
        PrintWriter pw = new PrintWriter(sw);
        job.getError().printStackTrace(pw);
      } else {
        sw.write(".\n");
      }

      Email mail = EmailBuilder.startingBlank()
                               .to(cfg.onErrorTo)
                               .from(cfg.from)
                               .withSubject(String.format("ChecklistBank job %s %s failed", job.getJobName(), job.getKey()))
                               .withPlainText(sw.toString())
                               .buildEmail();
      mailer.sendMail(mail, true);
    }
  }

  /**
   * Basic data for generating email bodies with freemarker templates.
   */
  public static class EmailData {
    private final UUID key;
    private final BackgroundJob job;
    private final User user;
    private final String from;
    private final String fromName;
    private final String replyTo;
    private final String domain;
    private final String subjectPrefix;

    public EmailData(BackgroundJob job, User user, MailConfig cfg) {
      this.job = job;
      this.key = job.getKey();
      this.user = user;
      if (cfg != null) {
        this.from = cfg.from;
        this.fromName = cfg.fromName;
        this.replyTo = cfg.replyTo;
        this.domain = cfg.domain;
        this.subjectPrefix = cfg.subjectPrefix;
      } else {
        this.from = null;
        this.fromName = null;
        this.replyTo = null;
        this.domain = null;
        this.subjectPrefix = null;
      }
    }

    public UUID getKey() {
      return key;
    }

    public String getFrom() {
      return from;
    }

    public String getFromName() {
      return fromName;
    }

    public String getReplyTo() {
      return replyTo;
    }

    public BackgroundJob getJob() {
      return job;
    }

    public User getUser() {
      return user;
    }

    public String getDomain() {
      return domain;
    }

    public String getSubjectPrefix() {
      return subjectPrefix;
    }
  }
}
