package life.catalogue.doi;

import com.google.common.eventbus.Subscribe;

import life.catalogue.WsServerConfig;
import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.model.*;
import life.catalogue.api.search.ExportSearchRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.ProjectSourceMapper;
import life.catalogue.doi.service.DatasetConverter;
import life.catalogue.doi.service.DoiException;
import life.catalogue.doi.service.DoiService;
import life.catalogue.release.ProjectRelease;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Class to listen to dataset deletions and update DataCite if needed for COL managed DOIs
 */
public class DatasetDeletionListener {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetDeletionListener.class);

  private final WsServerConfig cfg;
  private final SqlSessionFactory factory;
  private final DoiService doiService;
  private final DatasetConverter converter;

  public DatasetDeletionListener(WsServerConfig cfg, SqlSessionFactory factory, DoiService doiService) {
    this.cfg = cfg;
    this.factory = factory;
    this.doiService = doiService;
    this.converter = new DatasetConverter(cfg.portalURI, cfg.clbURI);
  }

  @Subscribe
  public void datasetChanged(DatasetChanged event){
    if (event.isDeletion()
      && event.old.getDoi() != null
      && event.old.getDoi().isCOL()
    ) {
      final DOI doi = event.old.getDoi();
      try {
        // if the release was still private, it only had a draft DOI which gets removed completely
        if (!doiService.delete(doi)) {
          // DOI was hidden only - make sure the URL is correct and points to CLB
          doiService.update(doi, converter.datasetURI(event.key, false));
        }
        // TODO: sources might also have a DOI which we need to remove or update depending on whether the DOI is shared between releases!!!
        // See also DatasetDAO which removed the sources already from the DB and cascades a project deletion to all its releases!!!

      } catch (DoiException e) {
        LOG.error("Error changing COL DOI {}", doi, e);
      }
    }
  }

}
