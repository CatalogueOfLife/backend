package life.catalogue.concurrent;

import life.catalogue.api.model.User;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.config.MailConfig;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public abstract class EmailNotificationTemplateTest {

  public abstract BackgroundJob buildJob();

  @Test
  public void templates() throws Exception {
    var cfg = new MailConfig();
    cfg.host = "localhost";
    cfg.from = "col@mailinator.com";
    cfg.fromName = "ChecklistBank";
    cfg.replyTo = "col@mailinator.com";

    BackgroundJob job = buildJob();
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