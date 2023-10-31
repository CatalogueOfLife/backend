package life.catalogue.admin.jobs;

import life.catalogue.api.model.RequestScope;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.es.NameUsageSearchService;

import org.apache.ibatis.session.SqlSessionFactory;

public class ReindexSchedulerJob extends DatasetSchedulerJob {
  private final NameUsageSearchService nuService;
  private final NameUsageIndexService indexService;

  public ReindexSchedulerJob(int userKey, double threshold, SqlSessionFactory factory, JobExecutor exec, NameUsageSearchService nuService, NameUsageIndexService indexService) {
    super(userKey, threshold, factory, exec);
    this.nuService = nuService;
    this.indexService = indexService;
  }

  @Override
  protected int countDone(int datasetKey) {
    return nuService.count(datasetKey);
  }

  @Override
  protected BackgroundJob buildJob(int datasetKey) {
    return new IndexJob(RequestScope.dataset(datasetKey), getUserKey(), JobPriority.MEDIUM, indexService);
  }
}