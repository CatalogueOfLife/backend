package life.catalogue.admin.jobs;

import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.dao.DaoUtils;
import life.catalogue.dao.NameDao;
import life.catalogue.dao.TaxonDao;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.NameIndexFactory;

import javax.validation.Validator;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resets all dataset sector counts for an entire catalogue, see param datasetKey,
 * and rebuilds the counts from the currently mapped sectors
 */
public class SectorCountJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(SectorCountJob.class);

  private final SqlSessionFactory factory;
  private final NameUsageIndexService indexService;
  private final Validator validator;
  @JsonProperty
  private final int datasetKey;

  public SectorCountJob(int userKey, SqlSessionFactory factory, NameUsageIndexService indexService, Validator validator, int datasetKey) {
    super(userKey);
    this.factory = factory;
    this.indexService = indexService;
    DaoUtils.requireProject(datasetKey);
    this.datasetKey = datasetKey;
    this.validator = validator;
  }

  @Override
  public void execute() throws Exception {
    NameDao ndao = new NameDao(factory, indexService, NameIndexFactory.passThru(), validator);
    TaxonDao tdao = new TaxonDao(factory, ndao, indexService, validator);
    LOG.info("Starting to update sector counts for dataset {}", datasetKey);
    tdao.updateAllSectorCounts(datasetKey);
    LOG.info("Finished updating sector counts for dataset {}", datasetKey);
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    if (other instanceof SectorCountJob) {
      SectorCountJob job = (SectorCountJob) other;
      return datasetKey == job.datasetKey;
    }
    return false;
  }
}
