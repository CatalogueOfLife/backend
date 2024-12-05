package life.catalogue.admin.jobs;

import com.google.common.eventbus.EventBus;

import life.catalogue.api.model.RequestScope;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.es.NameUsageSearchService;

import org.apache.ibatis.session.SqlSessionFactory;

import javax.annotation.Nullable;

public class ReindexSchedulerJob extends DatasetSchedulerJob {
  private final NameUsageSearchService nuService;
  private final NameUsageIndexService indexService;
  private final EventBus bus;

  /**
   * @param threshold the lowest percentage of names already matched that triggers a reindex.
   *                  Can be zero or negative to process all incomplete datasets even if a single record is missing.
   * @param bus event bus to send varnish cache flushes. If null no caches will be flushed
   */
  public ReindexSchedulerJob(int userKey, double threshold, SqlSessionFactory factory, JobExecutor exec, NameUsageSearchService nuService,
                             NameUsageIndexService indexService, @Nullable EventBus bus) {
    super(userKey, threshold, factory, exec);
    this.nuService = nuService;
    this.indexService = indexService;
    this.bus = bus;
  }

  @Override
  protected int countDone(int datasetKey) {
    return nuService.count(datasetKey);
  }

  @Override
  protected BackgroundJob buildJob(int datasetKey) {
    return new IndexJob(RequestScope.dataset(datasetKey), getUserKey(), JobPriority.MEDIUM, indexService, bus);
  }
}