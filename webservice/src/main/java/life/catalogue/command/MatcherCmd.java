package life.catalogue.command;

import com.codahale.metrics.MetricRegistry;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.dao.UserDao;
import life.catalogue.matching.UsageMatcherFactory;
import life.catalogue.matching.nidx.NameIndexFactory;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Manages on-disk usage-matcher indices stored in {@code cfg.matching.storageDir}.
 * Currently supports removing matchers whose name count falls below a minimum threshold
 * via {@code --delete-small N}, which frees disk space for small or stale datasets.
 * Single-dataset management via {@code --key} is not yet implemented.
 */
public class MatcherCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(MatcherCmd.class);
  private static final String ARG_KEY = "key";
  private static final String ARG_MINIMUM = "delete-small";
  private UsageMatcherFactory matcherFactory;

  public MatcherCmd() {
    super("matcher", "Manage matching indices");
  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    // Adds indexing options
    subparser.addArgument("--"+ ARG_KEY, "-k")
      .dest(ARG_KEY)
      .type(Integer.class)
      .required(false);
    subparser.addArgument("--"+ ARG_MINIMUM)
       .dest(ARG_MINIMUM)
       .type(Integer.class)
       .required(false);
  }

  @Override
  public void execute() throws Exception {
    System.out.println( String.format("Manage usage matcher indices in %s", cfg.matching.storageDir));
    JobExecutor executor = null;
    try (var ni = NameIndexFactory.build(cfg.namesIndex, factory, AuthorshipNormalizer.INSTANCE)) {
      UserDao udao = new UserDao(factory, cfg.mail, null, null, null);
      executor = new JobExecutor(cfg.job, new MetricRegistry(), null, udao);
      executor.start();
      matcherFactory = new UsageMatcherFactory(cfg.matching, ni, factory, executor);

      // key
      Integer key = ns.getInt(ARG_KEY);
      if (key != null) {
        throw new NotImplementedException();
      }

      Integer min = ns.getInt(ARG_MINIMUM);
      if (min != null) {
        deleteSmallMatcher(min);
      }

    } finally {
      executor.stop();
      matcherFactory.close();
    }
  }

  private void deleteSmallMatcher(Integer min) {
    matcherFactory.loadAllFromDisk();
    var matcher = matcherFactory.metadata(false);
    for (var m : matcher.matchers) {
      removeIfSmall(m.datasetKey, m.size, min);
    }
  }

  private void removeIfSmall(int datasetKey, int size, int min) {
    if (size < min) {
      LOG.info("Remove matcher for dataset {} with {} names only", datasetKey, size);
      matcherFactory.remove(datasetKey);
    }
  }
}
