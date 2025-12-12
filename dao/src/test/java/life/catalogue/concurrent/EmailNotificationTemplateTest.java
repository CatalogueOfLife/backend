package life.catalogue.concurrent;

import life.catalogue.api.model.User;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.config.MailConfig;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;

public abstract class EmailNotificationTemplateTest {

  public abstract BackgroundJob buildJob() throws IOException;

  @Test
  public void templates() throws Exception {
    testTemplates(buildJob());
  }

  public static void testTemplates(BackgroundJob job) throws Exception {
    var cfg = new MailConfig();
    cfg.host = "localhost";
    cfg.from = "col@mailinator.com";
    cfg.fromName = "ChecklistBank";
    cfg.replyTo = "col@mailinator.com";

    job.setUser(new User());
    job.getUser().setKey(77);
    job.getUser().setUsername("foo");
    job.getUser().setLastname("Bar");
    EmailNotification email = new EmailNotification(null, cfg);

    for (JobStatus status : JobStatus.values()) {
      if (status.isDone()) {
        job.setStatus(status);
        if (status == JobStatus.FAILED) {
          // provide some exception
          job.setError(new RuntimeException("This is not a real exception", new IllegalArgumentException("This is the root exception")));
        }
        String mail = email.buildEmailText(job);
        assertNotNull(mail);
        System.out.println(mail);
      }
    }
  }
}