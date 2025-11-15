package life.catalogue.gbifsync;

import com.google.common.annotations.VisibleForTesting;

import jakarta.ws.rs.WebApplicationException;

import life.catalogue.api.model.Publisher;
import life.catalogue.api.vocab.Country;
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
    LOG.info("Starting publisher registry sync");
    try (SqlSession session = sessionFactory.openSession(true)) {
      var dm = session.getMapper(DatasetMapper.class);
      var pm = session.getMapper(PublisherMapper.class);
      for (var key : dm.listPublisherKeys()) {
        Publisher p = getFromGBIF(key);
        if (p == null) {
          continue;
        }
        var existing = pm.get(key);
        if (existing == null) {
          pm.create(p);
          created++;
        } else if (!existing.equals(p)) {
          pm.update(p);
          updated++;
        }
      }
    }
    LOG.info("{} publisher added, {} updated", created, updated);
  }

  @VisibleForTesting
  Publisher getFromGBIF(UUID key) throws Exception {
    try {
      return orgTarget.path(key.toString())
        .request(MediaType.APPLICATION_JSON_TYPE)
        .get(GPublisher.class);
    } catch (WebApplicationException e) {
      LOG.warn("Publisher {} not found in GBIF", key, e);
      return null;
    }
  }

  public static class GPublisher extends Publisher {
    public void setHomepage(List<String> homepages) {
      if (homepages != null && !homepages.isEmpty()) {
        super.setHomepage(StringUtils.trimToNull(homepages.get(0)));
      } else {
        super.setHomepage(null);
      }
    }

    public void setCountry(String country) {
      if (StringUtils.isBlank(country)) {
        super.setCountry(null);
      } else {
        var opt = Country.fromIsoCode(country);
        if (opt.isPresent()) {
          super.setCountry(opt.get().getName());
        } else {
          super.setCountry(country);
        }
      }
    }
  }
}
