package life.catalogue.admin.jobs;

import life.catalogue.api.model.RequestScope;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.es.NameUsageIndexService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.slf4j.MDC;

public class IndexJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(IndexJob.class);

  private final NameUsageIndexService indexService;

  @JsonProperty
  private final RequestScope req;

  public IndexJob(RequestScope req, int userKey, JobPriority priority, NameUsageIndexService indexService) {
    super(priority, userKey);
    this.req = req;
    this.indexService = indexService;
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    if (other instanceof IndexJob) {
      IndexJob job = (IndexJob) other;
      return req.equals(job.req);
    }
    return false;
  }

  @Override
  public void execute() {
    // cleanup
    try {
      if (req.getDatasetKey() != null) {
        if (req.getSectorKey() != null) {
          LoggingUtils.setSectorAndDatasetMDC(req.getSectorKeyAsDSID(), null, getClass());
          LOG.info("Reindex sector {} by {}", req.getDatasetKey(), getUserKey());
          indexService.indexSector(req.getSectorKeyAsDSID());
        } else {
          LoggingUtils.setDatasetMDC(req.getDatasetKey(), getClass());
          LOG.info("Reindex dataset {} by {}", req.getDatasetKey(), getUserKey());
          indexService.indexDataset(req.getDatasetKey());
        }
      } else if (req.getAll()){
        LOG.warn("Reindex all datasets by {}", getUserKey());
        indexService.indexAll();
      } else {
        LOG.info("Bad reindex request {}", req);
      }
    } catch (RuntimeException e){
      LOG.error("Error reindexing", e);
    } finally {
      MDC.clear();
    }
  }
}
