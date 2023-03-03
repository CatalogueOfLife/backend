package life.catalogue.concurrent;

public class EmailNotificationTest extends EmailNotificationTemplateTest{

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
}