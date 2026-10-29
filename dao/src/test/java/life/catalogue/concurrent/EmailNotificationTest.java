package life.catalogue.concurrent;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.config.MailConfig;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EmailNotificationTest extends EmailNotificationTemplateTest{
  private static final String REASON = "The reason given was";

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

  @Override
  public BackgroundJob buildJob() {
    return new EmailJob();
  }

  private String failedMail(Throwable err) throws Exception {
    return failedMail(err, "col@mailinator.com");
  }

  private String failedMail(Throwable err, String replyTo) throws Exception {
    var job = buildJob();
    job.setUser(new User());
    job.getUser().setUsername("foo");
    job.setStatus(JobStatus.FAILED);
    job.setError(err);
    var cfg = new MailConfig();
    cfg.from = "col@mailinator.com";
    cfg.fromName = "ChecklistBank";
    cfg.replyTo = replyTo;
    return new EmailNotification(null, cfg).buildEmailText(job);
  }

  @Test
  public void reasonForRequestErrors() throws Exception {
    String mail = failedMail(new RuntimeException("wrapper", new IllegalArgumentException("scientificName column required")));
    assertTrue(mail.contains(REASON + ": scientificName column required"));
    assertTrue(failedMail(new NotFoundException("Root taxon x does not exist")).contains("Root taxon x does not exist"));
  }

  /**
   * Only request errors have a message meant for the user, others are just in the stacktrace of the footer.
   */
  @Test
  public void noReasonForInternalErrors() throws Exception {
    assertFalse(failedMail(new RuntimeException("org.postgresql.util.PSQLException: no column")).contains(REASON));
    assertFalse(failedMail(new NullPointerException("npe")).contains(REASON));
    assertFalse(failedMail(null).contains(REASON));
  }

  @Test
  public void supportFallback() throws Exception {
    assertTrue(failedMail(null, null).contains("support@catalogueoflife.org"));
  }
}
