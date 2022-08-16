package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.dw.mail.MailConfig;

import java.net.URI;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import com.codahale.metrics.MetricRegistry;

public class EmailNotificationTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.apple();

  @Test
  public void templates() throws Exception {
    WsServerConfig cfg = new WsServerConfig();
    cfg.apiURI = URI.create("http://api.dev.catalogueoflife.org");
    cfg.downloadURI = URI.create("http://download.dev.catalogueoflife.org");
    cfg.mail = new MailConfig();
    cfg.mail.host = "localhost";
    cfg.mail.from = "col@mailinator.com";
    cfg.mail.fromName = "Catalogue of Life";
    cfg.mail.replyTo = "col@mailinator.com";

    ExportRequest req = new ExportRequest(TestDataRule.APPLE.key, DataFormat.COLDP);

    Dataset d = new Dataset();
    d.setKey(1000);
    d.setTitle("My Big Day");

    MetricRegistry registry = new MetricRegistry();
    DatasetExport job = new DatasetExport(req, Users.TESTER, req.getFormat(), d, null, PgSetupRule.getSqlSessionFactory(), cfg, null, registry.timer("test.timer")) {
      @Override
      protected void export() throws Exception {
        System.out.println("EXPORT");
      }
    };

    for (JobStatus status : JobStatus.values()) {
      if (status.isDone()) {
        var exp = job.getExport();
        exp.setStatus(status);
        exp.setSize(1234567);
        String mail = EmailNotification.downloadMail(exp, d, TestDataRule.TEST_USER, cfg);
        System.out.println(mail);
        if (status == JobStatus.FINISHED) {
          exp.addTruncated(ColdpTerm.VernacularName);
          exp.addTruncated(ColdpTerm.Distribution);
          mail = EmailNotification.downloadMail(exp, d, TestDataRule.TEST_USER, cfg);
          System.out.println(mail);
        }
        // try also with version
        d.setVersion("12.8");
      }
    }
  }
}