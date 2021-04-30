package life.catalogue.dw.mail;


import org.simplejavamail.api.mailer.config.TransportStrategy;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.Map;

public class MailConfig {

  /**
   * The SMTP host. If not given no mail server will be setup.
   */
  public String host;
  public int port = 25;

  public String username;
  public String password;

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

  @NotNull
  public TransportStrategy transport = TransportStrategy.SMTP;
  public boolean debugOnly;

  /**
   * Builds a property map with configs suitable for simplejavamail
   */
  public Map<String, String> buildProperties() {
    return Map.of(
      "simplejavamail.defaults.from.name", fromName,
      "simplejavamail.defaults.from.address", from,
      "simplejavamail.defaults.replyto.name", fromName,
      "simplejavamail.defaults.replyto.address", from
    );
  }

}