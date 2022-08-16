package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.User;
import life.catalogue.common.lang.Exceptions;
import life.catalogue.db.mapper.UserMapper;
import life.catalogue.metadata.FmUtil;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.mailer.AsyncResponse;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.email.EmailBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import freemarker.template.TemplateException;

public class EmailNotification {
  private static final Logger LOG = LoggerFactory.getLogger(EmailNotification.class);

  private final Mailer mailer;
  private final SqlSessionFactory factory;
  private final WsServerConfig cfg;

  /**
   * @param mailer if null no mails will be sent
   */
  public EmailNotification(@Nullable Mailer mailer, SqlSessionFactory factory, WsServerConfig cfg) {
    this.mailer = mailer;
    this.factory = factory;
    this.cfg = cfg;
  }
  public void email(DatasetExport job) {
    try (SqlSession session = factory.openSession()) {
      User user = session.getMapper(UserMapper.class).get(job.getUserKey());
      if (user == null) {
        LOG.info("No user {} existing", job.getUserKey());
        return;
      }
      email(job.getExport(), job.dataset, user);
    }
  }

  public void email(final life.catalogue.api.model.DatasetExport export, Dataset dataset, User user) {
    final var status = export.getStatus();
    if (!status.isDone()) {
      LOG.info("Do not notify users about {} export {}", export.getStatus(), export.getKey());
      return;
    }

    String text = null;
    try {
      text = downloadMail(export, dataset, user, cfg);
      if (mailer != null) {
        Email mail = EmailBuilder.startingBlank()
          .to(user.getName(), user.getEmail())
          .from(cfg.mail.fromName, cfg.mail.from)
          .withReplyTo(cfg.mail.replyTo)
          .bccAddresses(cfg.mail.bcc)
          .withSubject(String.format("COL download %s %s", export.getKey(), export.getStatus().name().toLowerCase()))
          .withPlainText(text)
          .buildEmail();

        AsyncResponse asyncResp = mailer.sendMail(mail, true);
        asyncResp.onSuccess(() -> {
          LOG.info("Successfully sent mail for download {} to {}", export.getKey(), user.getEmail());
        });
        asyncResp.onException((e) -> {
          LOG.error("Error sending mail for download {} to {}", export.getKey(), user.getEmail(), e);
        });
        if (cfg.mail.block) {
          Future<?> f = asyncResp.getFuture();
          f.get(); // blocks
        }
        LOG.info("Sent email notification for download {} to user [{}] {} <{}>", export.getKey(), user.getKey(), user.getName(), user.getEmail());

      } else {
        LOG.warn("No mailer configured to sent email notification for download {} to user [{}] {} <{}>", export.getKey(), user.getKey(), user.getName(), user.getEmail());
        LOG.debug(text);
      }

    } catch (IOException | TemplateException | ExecutionException | InterruptedException | RuntimeException e) {
      LOG.error("Error sending mail for download {}", export.getKey(), e);
      if (text != null) {
        LOG.info(text);
      }
      if (cfg.mail.block) {
        throw Exceptions.asRuntimeException(e);
      }
    }
  }

  @VisibleForTesting
  static String downloadMail(life.catalogue.api.model.DatasetExport export, Dataset dataset, User user, WsServerConfig cfg) throws IOException, TemplateException{
    final DownloadModel data = new DownloadModel(export, dataset, user, cfg);
    final String template = "email/download-" + export.getStatus().name().toLowerCase() + ".ftl";
    return FmUtil.render(data, template);
  }

  public static class DownloadModel {
    private final UUID key;
    private final life.catalogue.api.model.DatasetExport export;
    private final User user;
    private final Dataset dataset;
    private final String from;
    private final String fromName;
    private final String replyTo;

    public DownloadModel(life.catalogue.api.model.DatasetExport export, Dataset dataset, User user, WsServerConfig cfg) {
      this.key = export.getKey();
      this.export = export;
      this.user = user;
      this.dataset = dataset;
      this.from = cfg.mail.from;
      this.fromName = cfg.mail.fromName;
      this.replyTo = cfg.mail.replyTo;
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

    public life.catalogue.api.model.DatasetExport getExport() {
      return export;
    }

    public User getUser() {
      return user;
    }

    public Dataset getDataset() {
      return dataset;
    }

  }
}
