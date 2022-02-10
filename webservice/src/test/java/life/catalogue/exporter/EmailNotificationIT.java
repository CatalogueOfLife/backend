package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.dw.mail.MailBundle;
import life.catalogue.dw.mail.MailConfig;
import life.catalogue.img.ImageService;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import com.codahale.metrics.MetricRegistry;

@Ignore("This actually sends mails. Requires GBIF server")
public class EmailNotificationIT {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.apple();

  @Test
  public void accept() throws Exception {
    WsServerConfig cfg = new WsServerConfig();
    cfg.apiURI = URI.create("http://api.dev.catalogueoflife.org");
    cfg.downloadURI = URI.create("http://download.dev.catalogueoflife.org");
    cfg.mail = new MailConfig();
    cfg.mail.host = "smtp.gbif.org";
    cfg.mail.from = "download@catalogueoflife.org";
    cfg.mail.fromName = "COL Downloads";
    cfg.mail.bcc.add("col@mailinator.com");
    cfg.mail.block = false;

    MailBundle bundle = new MailBundle();
    bundle.run(cfg, null);
    EmailNotification emailer = new EmailNotification(bundle.getMailer(), PgSetupRule.getSqlSessionFactory(), cfg);

    MetricRegistry registry = new MetricRegistry();
    ExportRequest req = new ExportRequest(TestDataRule.APPLE.key, DataFormat.COLDP);
    ColdpExporter job = new ColdpExporter(req, TestDataRule.TEST_USER.getKey(), PgSetupRule.getSqlSessionFactory(), cfg, ImageService.passThru(), registry.timer("test.timer"));

    for (JobStatus status : JobStatus.values()) {
      job.getExport().setStatus(status);
      emailer.email(job);
    }

    TimeUnit.MINUTES.sleep(1);
  }
}