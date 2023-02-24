package life.catalogue.concurrent;

import life.catalogue.api.model.User;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.config.MailConfig;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class EmailNotificationTest {

  public static class EmailJob extends BackgroundJob {

    protected EmailJob() {
      super(1);
    }

    @Override
    public void execute() throws Exception {
      System.out.println("Hey, job done.");
    }

    @Override
    public String getEmailTemplatePrefix() {
      return "job";
    }
  }

  @Test
  public void templates() throws Exception {
    var cfg = new MailConfig();
    cfg.host = "localhost";
    cfg.from = "col@mailinator.com";
    cfg.fromName = "Catalogue of Life";
    cfg.replyTo = "col@mailinator.com";

    EmailJob job = new EmailJob();
    job.setUser(new User());
    job.getUser().setKey(77);
    job.getUser().setUsername("foo");
    job.getUser().setLastname("Bar");
    EmailNotification email = new EmailNotification(null, cfg);

    for (JobStatus status : JobStatus.values()) {
      if (status.isDone()) {
        job.setStatus(status);
        String mail = email.buildEmailText(job);
        assertNotNull(mail);
        System.out.println(mail);
      }
    }
  }
}