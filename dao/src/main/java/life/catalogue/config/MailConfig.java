package life.catalogue.config;


import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.simplejavamail.api.mailer.config.TransportStrategy;

public class MailConfig {

  /**
   * The SMTP host. If not given no mail server will be setup.
   */
  public String host;

  @Min(1)
  public int port = 25;

  public String username;
  public String password;

  @NotNull
  public TransportStrategy transport = TransportStrategy.SMTP;

  /**
   * Maximum poolsize for threads to send mails asynchroneously.
   */
  @Min(1)
  public int threads = 3;

  /**
   * Default address to be used for from and reply-to settings.
   */
  @NotNull
  public String from;

  /**
   * Default name to be used for from and reply-to settings.
   */
  @NotNull
  public String fromName;

  /**
   * Default address to be used for replying.
   */
  @NotNull
  public String replyTo;

  /**
   * Optional address to sent mails to when background jobs throw an error
   */
  public String onErrorTo;

  @NotNull
  public List<String> bcc = new ArrayList<>();

  /**
   * Turns on debug logging and blocks async sending of mails, throwing in case mails cannot be sent.
   */
  public boolean block = false;

  /**
   * The prefix to be used for email subject lines.
   * Can be used to indicate a specific environment.
   */
  public String subjectPrefix = "ChecklistBank";

  /**
   * The domain both website and API are running under.
   * Used to build links.
   */
  public String domain = "checklistbank.org";

}