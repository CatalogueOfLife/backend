package life.catalogue.command;

import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Users;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.concurrent.NamedThreadFactory;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.jobs.RebuildMetricsJob;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.argparse4j.inf.Subparser;

public class TaxonMetricsCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(TaxonMetricsCmd.class);

  private static final String ARG_THREADS = "t";

  public TaxonMetricsCmd() {
    super("taxonMetrics", "Rebuild taxon metrics for all datasets");
  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    subparser.addArgument("-"+ ARG_THREADS)
      .dest(ARG_THREADS)
      .type(Integer.class)
      .required(true)
      .help("number of threads to use");
  }

  @Override
  public void execute() throws Exception {
    final int threads = ns.getInt(ARG_THREADS);
    List<Integer> keys;
    try (SqlSession session = factory.openSession()) {
      var dm = session.getMapper(DatasetMapper.class);
      DatasetSearchRequest req = new DatasetSearchRequest();
      req.setOrigin(List.of(DatasetOrigin.EXTERNAL, DatasetOrigin.RELEASE, DatasetOrigin.XRELEASE));
      keys = dm.searchKeys(req, userKey);
    }

    LOG.info("Rebuild metrics for {} datasets using {} threads", keys.size(), threads);
    ExecutorService exec = Executors.newFixedThreadPool(threads, new NamedThreadFactory("metrics-builder"));
    for (int key : keys) {
      var job = new RebuildMetricsJob(Users.DB_INIT, factory, key);
      exec.submit(job);
    }
    ExecutorUtils.shutdown(exec);
    LOG.info("Finished rebuilding metrics for all {} datasets", keys.size());
  }

}
