package life.catalogue.dao;

import life.catalogue.api.model.Publisher;
import life.catalogue.config.GbifConfig;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriBuilder;

/**
 * Wrapper to GBIF
 */
public class GbifPublisherDao {
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(GbifPublisherDao.class);
  private final WebTarget target;

  public GbifPublisherDao(GbifConfig cfg, Client client) {
    this.target = client.target(UriBuilder
      .fromUri(cfg.api)
      .path("/organization")
    );
  }

  public Publisher get(UUID id) {
    var pub = target.path(id.toString())
      .request()
      .accept(MediaType.APPLICATION_JSON_TYPE)
      .get(GAgent.class);
    return pub.toPublisher();
  }

  public static class GAgent {
    public String key;
    public String title;
    public String organization;
    public String city;
    public String province;
    public String country;
    public String latitude;
    public String longitude;
    public List<String> homepage;

    public Publisher toPublisher(){
      Publisher org = new Publisher();
      org.setTitle(title);
      org.setId(UUID.fromString(key));
      StringBuilder sb = new StringBuilder();
      addIfExists(sb, country);
      addIfExists(sb, province, ", ");
      addIfExists(sb, city, ", ");
      addIfExists(sb, organization, ". ");
      if (homepage != null) {
        for (var h : homepage) {
          addIfExists(sb, h, ". ");
        }
      }
      return org;
    }

    private void addIfExists(StringBuilder sb, String value) {
      addIfExists(sb, value, "");
    }
    private void addIfExists(StringBuilder sb, String value, String prefix) {
      if (!StringUtils.isBlank(value)) {
        if (sb.length() > 0) {
          sb.append(prefix);
        }
        sb.append(value.trim());
      }
    }

    @Override
    public String toString() {
      return "GPublisher{title=" + title + ", country=" + country + '}';
    }
  }

  static class GResp {
    public int count;
    public List<GAgent> results;
  }

  public List<Publisher> search(String q) {
    var wt = target
      .queryParam("q", q)
      .queryParam("numPublishedDatasets", "1,") // at least 1 dataset published
      .queryParam("offset", 0)
      .queryParam("limit", 100);
    var resp = wt.request()
      .accept(MediaType.APPLICATION_JSON_TYPE)
      .get(GResp.class);
    return resp.results.stream()
      .map(GAgent::toPublisher)
      .collect(Collectors.toList());
  }
}
