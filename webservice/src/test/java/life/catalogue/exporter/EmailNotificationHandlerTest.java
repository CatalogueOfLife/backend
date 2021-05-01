package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.common.concurrent.JobStatus;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.dw.mail.MailBundle;
import life.catalogue.dw.mail.MailConfig;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.*;

public class EmailNotificationHandlerTest {

  @Test
  public void templates() throws Exception {
    WsServerConfig cfg = new WsServerConfig();
    cfg.apiURI = URI.create("http://api.dev.catalogueoflife.org");
    cfg.downloadURI = URI.create("http://download.dev.catalogueoflife.org");
    cfg.mail = new MailConfig();
    cfg.mail.host = "localhost";
    cfg.mail.from = "col@mailinator.com";
    cfg.mail.fromName = "Catalogue of Life";

    ExportRequest req = ExportRequest.dataset(TestDataRule.APPLE.key, 1);

    Dataset d = new Dataset();
    d.setKey(1000);
    d.setTitle("My Big D");

    DatasetExporter job = new DatasetExporter(req, DataFormat.COLDP, d, null, cfg, null) {
      @Override
      protected void export() throws Exception {
        System.out.println("EXPORT");
      }
    };

    for (JobStatus status : JobStatus.values()) {
      if (status.isDone()) {
        job.setStatus(status);
        final String mail = EmailNotificationHandler.downloadMail(job, TestDataRule.TEST_USER, cfg);
        System.out.println(mail);
      }
    }
  }
}