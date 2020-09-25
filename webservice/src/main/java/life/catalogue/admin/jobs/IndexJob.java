package life.catalogue.admin.jobs;

import com.fasterxml.jackson.annotation.JsonProperty;
import life.catalogue.api.model.RequestScope;
import life.catalogue.api.model.User;
import life.catalogue.common.concurrent.BackgroundJob;
import life.catalogue.common.concurrent.JobPriority;
import life.catalogue.es.NameUsageIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(IndexJob.class);

  private final NameUsageIndexService indexService;

  @JsonProperty
  private final RequestScope req;

  public IndexJob(RequestScope req, User user, JobPriority priority, NameUsageIndexService indexService) {
    super(priority, user.getKey());
    this.req = req;
    this.indexService = indexService;
  }

  @Override
  public void execute() {
    // cleanup
    try {
      if (req.getDatasetKey() != null) {
        LOG.info("Reindex dataset {} by {}", req.getDatasetKey(), getUserKey());
        indexService.indexDataset(req.getDatasetKey());
      } else {
        LOG.warn("Reindex all datasets by {}", getUserKey());
        indexService.indexAll();
      }
    } catch (RuntimeException e){
      LOG.error("Error reindexing", e);
    }
  }
}
