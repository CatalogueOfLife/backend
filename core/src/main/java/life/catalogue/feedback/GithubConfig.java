package life.catalogue.feedback;

import java.net.URI;
import java.util.List;

import ch.qos.logback.classic.spi.ILoggingEvent;
import jakarta.validation.constraints.NotNull;

import org.apache.poi.ss.formula.functions.T;

public class GithubConfig {

  @NotNull
  public URI api = URI.create("https://api.github.com");

  @NotNull
  public String organisation;

  @NotNull
  public String repository;

  @NotNull
  public String token;

  public String encryptPassword;

  public String encryptSalt;

  public Tagging base = new Tagging();

  public Tagging xr = new Tagging();

  public URI issueURI() {
    return api.resolve("/repos/"+organisation+"/"+repository+"/issues");
  }

  public static class Tagging {
    public List<String> assignee;

    public List<String> labels;

    @Override
    public String toString() {
      return "{" +
        "assignee=" + assignee +
        ", labels=" + labels +
        '}';
    }
  }

  @Override
  public String toString() {
    return "api=" + api +
      ", organisation='" + organisation + '\'' +
      ", repository='" + repository + '\'' +
      ", base=" + base +
      ", xr=" + xr;
  }
}
