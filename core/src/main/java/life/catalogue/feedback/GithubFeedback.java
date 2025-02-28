package life.catalogue.feedback;

import com.google.common.annotations.VisibleForTesting;

import jakarta.ws.rs.core.UriBuilder;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameUsage;
import life.catalogue.api.model.User;
import life.catalogue.db.mapper.NameUsageMapper;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

import javax.annotation.Nullable;

/**
 * https://docs.github.com/en/rest/issues/issues?apiVersion=2022-11-28#create-an-issue
 */
public class GithubFeedback implements FeedbackService {
  private static final Logger LOG = LoggerFactory.getLogger(GithubFeedback.class);
  private final GithubConfig cfg;
  private final WebTarget issue;
  private final SqlSessionFactory factory;
  private final SpamDetector spamDetector;
  private final UriBuilder clbTaxonURI;
  private boolean active;

  public GithubFeedback(GithubConfig cfg, URI clbURI, Client client, SqlSessionFactory factory) {
    this.cfg = cfg;
    this.factory = factory;
    this.issue = client == null ? null : client.target(cfg.issueURI()); // null for tests only!
    this.clbTaxonURI = UriBuilder.fromUri(clbURI).path("dataset/{arg1}/nameusage/{arg2}");
    spamDetector = new SpamDetector();
  }

  @VisibleForTesting
  protected String buildMessage(Optional<User> user, DSID<String> usageKey, Feedback feedback, @Nullable String name) {
    StringBuilder msg = new StringBuilder();
    if (name != null) {
      msg.append(name)
         .append("\n\n");
    }
    msg.append(feedback.message);
    msg.append("\n\n---\n");
    msg.append(clbTaxonURI.build(usageKey.getDatasetKey(), usageKey.getId()));
    if (user.isPresent()) {
      msg.append("\nSubmitted by: "+user.get().getKey());
    }
    if (feedback.email != null) {
      msg.append("\nEmail: ").append(feedback.email);
    }
    return msg.toString();
  }

  @Override
  public URI create(Optional<User> user, DSID<String> usageKey, Feedback feedback) throws NotFoundException, IOException {
    if (feedback == null) {
      throw new IllegalArgumentException("No feedback provided");
    }
    if (!active) {
      throw UnavailableException.unavailable("feedback service");
    }
    if (spamDetector.isSpam(feedback.message)) {
      throw new IllegalArgumentException("Invalid message");
    }

    String name = null;
    StringBuilder title = new StringBuilder("Feedback on ");
    if (factory != null) {
      try (SqlSession session = factory.openSession()) {
        var num = session.getMapper(NameUsageMapper.class);
        var tax = num.getSimple(usageKey);
        if (tax == null) {
          throw NotFoundException.notFound(NameUsage.class, usageKey);
        }
        name = tax.getLabel();
        title.append(name);
      }
    }
    var iss = new GHIssue(title.toString(), buildMessage(user, usageKey, feedback, name), cfg.assignee, cfg.labels);
    var req = issue.request(MediaType.APPLICATION_JSON_TYPE)
      .header(HttpHeaders.AUTHORIZATION, "Bearer "+cfg.token)
      .header("User-Agent", "CatalogueOfLife")
      .header("X-GitHub-Api-Version", "2022-11-28")
      .header("Accept", "application/vnd.github+json");

    try (var resp = req.post(Entity.json(iss))) {
      if (resp.getStatus() != 201) {
        String respMsg = null;
        if (resp.getEntity() != null) {
          respMsg = resp.readEntity(String.class);
        }
        LOG.error("GitHub response {}: {}", resp.getStatus(), respMsg);
        throw new IOException("GitHub error "+resp.getStatus()+": "+respMsg);
      }
      GHIssueResp ghResp = resp.readEntity(GHIssueResp.class);
      LOG.info("GitHub issue created for taxon {}: {}", usageKey, ghResp.html_url);
      return URI.create(ghResp.html_url);
    }
  }

  @Override
  public void start() throws Exception {
    active = true;
  }

  @Override
  public void stop() throws Exception {
    active = false;
  }

  @Override
  public boolean hasStarted() {
    return active;
  }

  private static class GHIssue {
    public String title;
    public String body;
    public List<String> assignees;

    public GHIssue(String title, String body, List<String> assignees, List<String> labels) {
      this.title = title;
      this.body = body;
      this.assignees = assignees;
      this.labels = labels;
    }

    public List<String> labels;
  }

  private static class GHIssueResp {
    public String id;
    public String url;
    public String html_url;
    public Integer number;
    public String state;
    public String title;
  }
}
