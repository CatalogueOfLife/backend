package life.catalogue.exporter;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import freemarker.template.TemplateException;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.User;
import life.catalogue.common.concurrent.BackgroundJob;
import life.catalogue.common.concurrent.JobStatus;
import life.catalogue.common.text.StringUtils;
import life.catalogue.db.mapper.UserMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.mailer.AsyncResponse;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.email.EmailBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class EmailNotificationHandler implements Consumer<BackgroundJob> {
  private static final Logger LOG = LoggerFactory.getLogger(EmailNotificationHandler.class);

  private final Mailer mailer;
  private final SqlSessionFactory factory;
  private final WsServerConfig cfg;

  public EmailNotificationHandler(Mailer mailer, SqlSessionFactory factory, WsServerConfig cfg) {
    this.mailer = Preconditions.checkNotNull(mailer);
    this.factory = factory;
    this.cfg = cfg;
  }

  @Override
  public void accept(BackgroundJob job) {
    // make sure we only listen to DatasetExporter jobs!!!
    DatasetExporter exp = (DatasetExporter)job;

    final var status = job.getStatus();
    if (!status.isDone()) {
      throw new IllegalStateException("Email handler should not see " + job.getStatus() + " job.");
    }

    User user;
    try (SqlSession session = factory.openSession()) {
      user = session.getMapper(UserMapper.class).get(exp.getUserKey());
      if (user == null) {
        LOG.info("No user {} existing", exp.getUserKey());
        return;
      }
    }

    try {
      Email mail = EmailBuilder.startingBlank()
        .to(user.getName(), user.getEmail())
        .to("Batman", "Batman1973@mailinator.com")
        .from(cfg.mail.fromName, cfg.mail.from)
        .bccAddresses(cfg.mail.bcc)
        .withSubject(String.format("COL download %s %s", job.getKey(), job.getStatus().name().toLowerCase()))
        .withPlainText(downloadMail(exp, user, cfg))
        .buildEmail();
      AsyncResponse asyncResp = mailer.sendMail(mail, true);
      asyncResp.onSuccess(() -> {
        LOG.info("Successfully sent mail for download {}", job.getKey());
      });
      asyncResp.onException((e) -> {
        LOG.error("Error sending mail for download {}", job.getKey(), e);
      });
      if (cfg.mail.block) {
        Future<?> f = asyncResp.getFuture();
        f.get(); // blocks
      }
      LOG.info("Sent email notification for download {} to user {} {}<{}>", job.getKey(), user.getKey(), user.getName(), user.getEmail());

    } catch (IOException | TemplateException | ExecutionException | InterruptedException e) {
      LOG.error("Error sending mail for download {}", job.getKey(), e);
      if (cfg.mail.block) {
        throw new RuntimeException(e);
      }
    }
  }

  @VisibleForTesting
  static String downloadMail(DatasetExporter job, User user, WsServerConfig cfg) throws IOException, TemplateException{
    final DownloadModel data = new DownloadModel(job, user, cfg);
    final String template = "email/download-" + job.getStatus().name().toLowerCase() + ".ftl";
    return FmUtil.render(data, template);
  }

  public static class DownloadModel {
    private final UUID key;
    private final ExportRequest req;
    private final User user;
    private final Dataset dataset;
    private final URI download;
    private final JobStatus status;
    private final String size;
    private final String from;
    private final String fromName;

    public DownloadModel(DatasetExporter job, User user, WsServerConfig cfg) {
      this.key = job.getKey();
      this.req = job.req;
      this.user = user;
      this.dataset = job.dataset;
      this.from = cfg.mail.from;
      this.fromName = cfg.mail.fromName;
      this.download = cfg.archiveURI(job.getKey());;
      this.status = job.getStatus();
      File f = job.archive;
      String humanSize;
      try {
        humanSize = StringUtils.humanReadableByteSize(Files.size(f.toPath()));
      } catch (IOException e) {
        LOG.error("Failed to read filesize of download at {}", f, e);
        humanSize = "unknown";
      }
      this.size = humanSize;
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

    public String getSize() {
      return size;
    }

    public ExportRequest getReq() {
      return req;
    }

    public User getUser() {
      return user;
    }

    public Dataset getDataset() {
      return dataset;
    }

    public URI getDownload() {
      return download;
    }

    public JobStatus getStatus() {
      return status;
    }
  }
}
