package life.catalogue.admin.jobs;

import com.fasterxml.jackson.annotation.JsonProperty;
import life.catalogue.common.concurrent.BackgroundJob;
import life.catalogue.dao.DaoUtils;
import life.catalogue.dao.NameDao;
import life.catalogue.dao.TaxonDao;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.NameIndexFactory;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SectorCountJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(SectorCountJob.class);

  private final SqlSessionFactory factory;
  private final NameUsageIndexService indexService;
  @JsonProperty
  private final int datasetKey;

  public SectorCountJob(int userKey, SqlSessionFactory factory, NameUsageIndexService indexService, int datasetKey) {
    super(userKey);
    this.factory = factory;
    this.indexService = indexService;
    DaoUtils.requireProject(datasetKey);
    this.datasetKey = datasetKey;
  }

  @Override
  public void execute() throws Exception {
    NameDao ndao = new NameDao(factory, indexService, NameIndexFactory.passThru());
    TaxonDao tdao = new TaxonDao(factory, ndao, indexService);
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
