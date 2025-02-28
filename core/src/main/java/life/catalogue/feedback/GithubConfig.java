package life.catalogue.feedback;

import jakarta.validation.constraints.NotNull;

import java.net.URI;
import java.util.List;

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
}
