package life.catalogue.feedback;

import java.net.URI;
import java.util.List;

import jakarta.validation.constraints.NotNull;

public class GithubConfig {

  @NotNull
  public URI api = URI.create("https://api.github.com");

  @NotNull
  public String organisation;

  @NotNull
  public String repository;

  @NotNull
  public String token;

  public List<String> assignee;

  public List<String> labels;

  public String encryptPassword;

  public String encryptSalt;

  public URI issueURI() {
    return api.resolve("/repos/"+organisation+"/"+repository+"/issues");
  }

  @Override
  public String toString() {
    return "api=" + api +
      ", organisation='" + organisation + '\'' +
      ", repository='" + repository + '\'' +
      ", assignee=" + assignee +
      ", labels=" + labels;
  }
}
