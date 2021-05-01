package life.catalogue.dw.mail;


import org.simplejavamail.api.mailer.config.TransportStrategy;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public class MailConfig {

  /**
   * The SMTP host. If not given no mail server will be setup.
   */
  @NotNull
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
  public String fromName;

  @NotNull
  public List<String> bcc = new ArrayList<>();

  /**
   * Turns on debug logging and blocks async sending of mails, throwing in case mails cannot be sent.
   */
  public boolean block = false;

}