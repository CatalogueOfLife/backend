package life.catalogue.gbifsync;

import life.catalogue.api.model.Publisher;
import life.catalogue.concurrent.GlobalBlockingJob;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.PublisherMapper;

import java.util.UUID;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.WebApplicationException;

public class PublisherSyncJob extends GlobalBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(PublisherSyncJob.class);

  private final SqlSessionFactory sessionFactory;
  private final GbifRegistryCache registry;
  private int created;
  private int updated;

  /**
   *  Syncs all publishers found in all datasets, reusing the shared registry cache so organisations already
   *  fetched by the dataset sync are not requested from the slow GBIF registry again.
   **/
  public PublisherSyncJob(GbifRegistryCache registry, SqlSessionFactory sessionFactory, int userKey) {
    super(userKey, JobPriority.HIGH);
    this.sessionFactory = sessionFactory;
    this.registry = registry;
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

  private Publisher getFromGBIF(UUID key) {
    try {
      return registry.publisherEntity(key);
    } catch (WebApplicationException e) {
      LOG.warn("Publisher {} not found in GBIF", key, e);
      return null;
    }
  }
}
