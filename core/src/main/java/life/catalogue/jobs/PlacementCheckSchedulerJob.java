package life.catalogue.jobs;

import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.es.indexing.NameUsageIndexService;
import life.catalogue.matching.UsageMatcherFactory;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Schedules a {@link PlacementCheckJob} for every public external dataset of a given type, e.g. all Plazi articles.
 */
public class PlacementCheckSchedulerJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(PlacementCheckSchedulerJob.class);
  private final DatasetType type;
  private final List<String> references;
  private final SqlSessionFactory factory;
  private final JobExecutor exec;
  private final UsageMatcherFactory matcherFactory;
  private final DatasetImportDao diDao;
  private final NameUsageIndexService indexService;

  public record Params(DatasetType type, List<String> references) {
  }

  public PlacementCheckSchedulerJob(int userKey, DatasetType type, List<String> references, SqlSessionFactory factory, JobExecutor exec,
                                    UsageMatcherFactory matcherFactory, DatasetImportDao diDao, NameUsageIndexService indexService) {
    super(userKey);
    this.type = type;
    this.references = references;
    this.factory = factory;
    this.exec = exec;
    this.matcherFactory = matcherFactory;
    this.diDao = diDao;
    this.indexService = indexService;
  }

  @Override
  public Object getParams() {
    return new Params(type, references);
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    return other instanceof PlacementCheckSchedulerJob job && job.type == type;
  }

  @Override
  public void execute() throws Exception {
    var req = new DatasetSearchRequest();
    req.setType(List.of(type));
    req.setOrigin(List.of(DatasetOrigin.EXTERNAL));
    req.setPrivat(false);
    List<Integer> keys;
    try (SqlSession session = factory.openSession()) {
      keys = session.getMapper(DatasetMapper.class).searchKeys(req, DatasetMapper.MAGIC_ADMIN_USER_KEY);
    }
    LOG.info("Schedule placement checks for {} {} datasets against {}", keys.size(), type, references);
    for (int key : keys) {
      exec.submit(new PlacementCheckJob(getUserKey(), key, references, factory, matcherFactory, diDao, indexService));
    }
  }
}
