package org.col.task.gbifsync;

import com.google.common.collect.ImmutableMultimap;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.Dataset;
import org.col.task.common.BaseTask;
import org.col.db.mapper.DatasetMapper;
import org.col.task.common.GbifConfig;
import org.glassfish.jersey.client.rx.RxClient;
import org.glassfish.jersey.client.rx.java8.RxCompletionStageInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.List;

/**
 * Syncs datasets from the GBIF registry
 */
public class GbifSyncTask extends BaseTask {
  private static final Logger LOG = LoggerFactory.getLogger(GbifSyncTask.class);

  private final RxClient<RxCompletionStageInvoker> rxClient;
  private final SqlSessionFactory sessionFactory;
  private final GbifConfig gbif;

  public GbifSyncTask(GbifConfig gbif, SqlSessionFactory sessionFactory, RxClient<RxCompletionStageInvoker> rxClient) {
    super("gbifsync");
    this.gbif = gbif;
    this.rxClient = rxClient;
    this.sessionFactory = sessionFactory;
  }

  private void sync(DatasetPager pager, DatasetMapper mapper) throws Exception {
    int created = 0;
    int updated = 0;
    int deleted = 0;

    while (pager.hasNext()) {
      List<Dataset> page = pager.next();
      for (Dataset gbif : page) {
        Dataset curr = mapper.getByGBIF(gbif.getGbifKey());
        if (curr == null) {
          // create new dataset
          mapper.create(gbif);
          created++;
          LOG.info("New dataset added from GBIF: {} - {}", gbif.getKey(), gbif.getTitle());

        } else
          /**
           * we modify core metadata (title, description, contacts, version) via the dwc archive metadata
           * gbif syncs only change one of the following
           *  - dwca access url
           *  - license
           *  - organization (publisher)
           *  - homepage
           */
          if (!gbif.getDataAccess().equals(curr.getDataAccess()) ||
              !gbif.getLicense().equals(curr.getLicense()) ||
              !gbif.getOrganisation().equals(curr.getOrganisation()) ||
              !gbif.getHomepage().equals(curr.getHomepage())
              ) {
            curr.setDataAccess(gbif.getDataAccess());
            curr.setLicense(gbif.getLicense());
            curr.setOrganisation(gbif.getOrganisation());
            curr.setHomepage(gbif.getHomepage());
            mapper.update(curr);
            updated++;
        }
      }
    }
    //TODO: delete datasets no longer in GBIF
    LOG.info("{} datasets added, {} updated, {} deleted", created, updated, deleted);
  }

  @Override
  public void run(ImmutableMultimap<String, String> params, PrintWriter out) throws Exception {
    try (SqlSession session = sessionFactory.openSession(true)) {
      DatasetPager pager = new DatasetPager(rxClient, gbif);
      DatasetMapper mapper = session.getMapper(DatasetMapper.class);
      LOG.info("Syncing datasets from GBIF registry {}", gbif.api);
      sync(pager, mapper);
      session.commit();

    } catch (RuntimeException e) {
      LOG.error("Failed to sync with GBIF", e);
    }
  }
}
