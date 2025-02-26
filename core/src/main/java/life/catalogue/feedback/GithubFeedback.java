package life.catalogue.feedback;

import life.catalogue.api.exception.NotFoundException;
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

/**
 * https://docs.github.com/en/rest/issues/issues?apiVersion=2022-11-28#create-an-issue
 */
public class GithubFeedback implements FeedbackService {
  private static final Logger LOG = LoggerFactory.getLogger(GithubFeedback.class);
  private final GithubConfig cfg;
  private final WebTarget issue;
  private final SqlSessionFactory factory;

  public GithubFeedback(GithubConfig cfg, Client client, SqlSessionFactory factory) {
    this.cfg = cfg;
    this.factory = factory;
    this.issue = client.target(cfg.issueURI());
  }

  @Override
  public URI create(Optional<User> user, DSID<String> usageKey, String message) throws NotFoundException, IOException {
    StringBuilder title = new StringBuilder("Feedback on ");
    if (factory != null) {
      try (SqlSession session = factory.openSession()) {
        var num = session.getMapper(NameUsageMapper.class);
        var tax = num.getSimple(usageKey);
        if (tax == null) {
          throw NotFoundException.notFound(NameUsage.class, usageKey);
        }
        title.append(tax.getName());
      }
    }
    StringBuilder msg = new StringBuilder(message);
    if (user.isPresent()) {
      msg.append("\n---\n")
         .append("Submitted by: "+user.get().getKey());
    }
    var iss = new GHIssue(title.toString(), msg.toString(), cfg.assignee, cfg.labels);

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
