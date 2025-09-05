package life.catalogue.release;

import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DatasetListener;
import life.catalogue.api.model.*;
import life.catalogue.api.search.ExportSearchRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.common.io.PathUtils;
import life.catalogue.concurrent.JobConfig;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.dao.DatasetExportDao;
import life.catalogue.dao.DatasetSourceDao;
import life.catalogue.dao.NameUsageArchiver;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetSourceMapper;
import life.catalogue.db.mapper.PublisherMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.doi.service.DatasetConverter;
import life.catalogue.doi.service.DoiException;
import life.catalogue.doi.service.DoiService;

import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Listens to dataset changes and checks if the GBIF publisher of a dataset has changed.
 * If so, it looks in all projects for merge sectors from that dataset.
 * If in a project with such a sector there is also a sector publisher with the former publisher key
 * of the changed dataset, the new publisher will be added to the sector publishers of the project
 * if its not already present.
 *
 * See https://github.com/CatalogueOfLife/backend/issues/1423
 */
public class PublisherChangeListener implements DatasetListener {
  private static final Logger LOG = LoggerFactory.getLogger(PublisherChangeListener.class);
  private final SqlSessionFactory factory;

  public PublisherChangeListener(SqlSessionFactory factory) {
    this.factory = factory;
  }

  @Override
  public void datasetChanged(DatasetChanged event){
    if (event.isUpdated() // assures we got both obj and old
      && !Objects.equals(event.old.getGbifPublisherKey(), event.obj.getGbifPublisherKey())
    ) {
      LOG.info("Publisher of dataset {} has changed from {} to {}", event.obj.getKey(), event.old.getGbifPublisherKey(), event.obj.getGbifPublisherKey());
      try (SqlSession session = factory.openSession(true)) {
        var dm = session.getMapper(DatasetMapper.class);
        for (var pkey : dm.listProjectKeys()) {
          updateProject(pkey, event.obj, event.old.getGbifPublisherKey(), event.obj.getGbifPublisherKey(), session);
        }
      }
    }
  }

  private void updateProject(Integer pkey, Dataset obj, UUID oldPublisherKey, UUID newPublisherKey, SqlSession session) {
    var pm = session.getMapper(PublisherMapper.class);
    var currPublisher = pm.listAllKeys(pkey);
    if (currPublisher.contains(oldPublisherKey) && !currPublisher.contains(newPublisherKey)) {
      var sm = session.getMapper(SectorMapper.class);
      var sectors = sm.listByDataset(pkey, obj.getKey(), Sector.Mode.MERGE);
      if (!sectors.isEmpty()) {
        LOG.warn("Project {} has sectors from publisher {} which have changed to the new publisher {}. Adding new publisher to project!", obj.getKey(), oldPublisherKey, newPublisherKey);
        var p = new Publisher();
        p.setDatasetKey(pkey);
        p.setId(newPublisherKey);
        //TODO: query GBIF API
        p.setTitle("New publisher " + newPublisherKey);
        p.setAlias(newPublisherKey.toString().substring(0, 8));
        pm.create(p);
      }
    }
  }

}
