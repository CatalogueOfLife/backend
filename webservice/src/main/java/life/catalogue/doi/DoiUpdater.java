package life.catalogue.doi;

import life.catalogue.WsServerConfig;
import life.catalogue.api.event.DoiChange;

import com.google.common.eventbus.Subscribe;

import life.catalogue.api.model.DOI;
import life.catalogue.api.model.DSID;
import life.catalogue.doi.service.DatasetConverter;
import life.catalogue.doi.service.DoiConfig;
import life.catalogue.doi.service.DoiException;
import life.catalogue.doi.service.DoiService;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to update or delete DOI metadata when datasets change or get deleted.
 * DOIs for releases and projects are 1:1, so its simple to delete or update them.
 *
 * For release sources things are more complex as we share the same DOI for the same source in multiple releases
 * in case the source data has not changed. This means the same DOI applies to multiple archived source datasets.
 *
 * As a rule the URL designated in the DOI metadata should always refer to the earliest release when the DOI was first minted.
 * TODO: We might need to rethink this rule and point it to the first annual release (if existing) as these releases will not be deleted.
 */
public class DoiUpdater {
  private static final Logger LOG = LoggerFactory.getLogger(DoiUpdater.class);
  private final WsServerConfig cfg;
  private final SqlSessionFactory factory;
  private final DoiService doiService;
  private final DatasetConverter converter;
  private final Set<DOI> deleted = ConcurrentHashMap.newKeySet();

  public DoiUpdater(WsServerConfig cfg, SqlSessionFactory factory, DoiService doiService) {
    this.cfg = cfg;
    this.factory = factory;
    this.doiService = doiService;
    this.converter = new DatasetConverter(cfg.portalURI, cfg.clbURI);
  }

  /**
   * Updates or deletes the DOI metadata in DataCite. This can happen if dataset metadata has changed but also if a release was added or removed.
   * In case an entire project gets deleted
   * which removed the sources already from the DB and cascades a project deletion to all its releases!!!
   */
  @Subscribe
  public void update(DoiChange event){
    if (event.getDoi().isCOL()) {
      try {
        // a dataset/release DOI
        int key = event.getDoi().datasetKey();
        if (event.isDelete()) {
          delete(event.getDoi(), key);
        } else if (!deleted.contains(event.getDoi())){
          update(event.getDoi(), key);
        }

      } catch (IllegalArgumentException e) {
        // a source dataset DOI
        DSID<Integer> key = event.getDoi().sourceDatasetKey();
        if (event.isDelete()) {
          delete(event.getDoi(), key.getDatasetKey(), key.getId());
        } else if (!deleted.contains(event.getDoi())){
          update(event.getDoi(), key.getDatasetKey(), key.getId());
        }
      }
    }
  }

  private void update(DOI doi, int datasetKey) {
    LOG.warn("DOI update not implemented yet: {} for dataset {}", doi, datasetKey);
  }

  private void update(DOI doi, int datasetKey, int sourceDatasetKey) {
    LOG.warn("DOI update not implemented yet: {} for source dataset {}-{}", doi, datasetKey, sourceDatasetKey);
  }


  private void delete(DOI doi, int datasetKey){
    delete(doi, converter.datasetURI(datasetKey, false));
  }

  private void delete(DOI doi, int datasetKey, int sourceDatasetKey){
    delete(doi, converter.sourceURI(datasetKey, sourceDatasetKey, false));
  }

  private void delete(DOI doi, URI url){
    try {
      // if the release was still private, it only had a draft DOI which gets removed completely
      if (!doiService.delete(doi)) {
        // DOI was hidden only - make sure the URL is correct and points to CLB
        doiService.update(doi, url);
      }
      deleted.add(doi);
      // sources might also have a DOI which we need to remove or update depending on whether the DOI is shared between releases.
      // This is managed by triggering a DoiUpdate event for each of the DOIs in the DatasetDAO
    } catch (DoiException e) {
      LOG.error("Error deleting COL DOI {}", doi, e);
    }
  }

}
