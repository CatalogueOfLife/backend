package life.catalogue.gbifsync;

import jakarta.ws.rs.WebApplicationException;

import life.catalogue.api.model.Publisher;
import life.catalogue.concurrent.GlobalBlockingJob;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.config.GbifConfig;
import life.catalogue.dao.PublisherDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.PublisherMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriBuilder;

public class PublisherSyncJob extends GlobalBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(PublisherSyncJob.class);

  private final WebTarget orgTarget;
  private final SqlSessionFactory sessionFactory;
  private Set<UUID> keys;
  private int created;
  private int updated;
  private int deleted;

  /**
   *  Syncs all publishers found in all datasets
   **/
  public PublisherSyncJob(GbifConfig cfg, Client client, SqlSessionFactory sessionFactory, int userKey) {
    super(userKey, JobPriority.HIGH);
    this.sessionFactory = sessionFactory;
    this.keys = keys == null ? new HashSet<>() : keys;
    this.orgTarget = client.target(UriBuilder
      .fromUri(cfg.api)
      .path("/organization")
    );
  }

  @Override
  public void execute() throws Exception {
    try (SqlSession session = sessionFactory.openSession(true)) {
      var dm = session.getMapper(DatasetMapper.class);
      var pm = session.getMapper(PublisherMapper.class);
      for (var key : dm.listPublisherKeys()) {
        Publisher p = getFromGBIF(key);
        if (p == null) {
          continue;
        }
        if (!pm.exists(key)) {
          pm.create(p);
          created++;
        } else {
          var existing = pm.get(key);
          if (!existing.equals(p)) {
            pm.update(p);
            updated++;
          }
        }
      }
    }
    LOG.info("{} publisher added, {} updated", created, updated);
  }

  private Publisher getFromGBIF(UUID key) throws Exception {
    try {
      return orgTarget.path(key.toString())
        .request(MediaType.APPLICATION_JSON_TYPE)
        .get(Publisher.class);
    } catch (WebApplicationException e) {
      LOG.warn("Publisher {} not found in GBIF", key, e);
      return null;
    }
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
      org.setKey(UUID.fromString(key));
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
}
