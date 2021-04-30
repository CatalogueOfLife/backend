package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.common.concurrent.JobStatus;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.dw.mail.MailBundle;
import life.catalogue.img.ImageService;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.net.URI;

public class EmailNotificationHandlerIT {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.apple();

  @Test
  public void accept() throws Exception {
    WsServerConfig cfg = new WsServerConfig();
    cfg.apiURI = URI.create("http://api.dev.catalogueoflife.org");
    cfg.downloadURI = URI.create("http://download.dev.catalogueoflife.org");
    cfg.mail.host = "smtp.gbif.org";
    cfg.mail.from = "col@mailinator.com";
    cfg.mail.fromName = "Catalogue of Life";
    cfg.mail.bcc.add("col2@mailinator.com");
    cfg.mail.block = true;

    MailBundle bundle = new MailBundle();
    bundle.run(cfg, null);
    EmailNotificationHandler handler = new EmailNotificationHandler(bundle.getMailer(), PgSetupRule.getSqlSessionFactory(), cfg);

    ExportRequest req = ExportRequest.dataset(TestDataRule.APPLE.key, TestDataRule.TEST_USER.getKey());
    ColdpExporter job = new ColdpExporter(req, PgSetupRule.getSqlSessionFactory(), cfg, ImageService.passThru());

    for (JobStatus status : JobStatus.values()) {
      if (status.isDone()) {
        job.setStatus(status);
        handler.accept(job);
      }
    }
  }
}