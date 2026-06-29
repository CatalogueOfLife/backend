package life.catalogue.command;

import com.codahale.metrics.MetricRegistry;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.dao.UserDao;
import life.catalogue.matching.UsageMatcherFactory;
import life.catalogue.matching.nidx.NameIndexFactory;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Manages on-disk usage-matcher indices stored in {@code cfg.matching.storageDir}.
 * Supported actions (pick one):
 * <ul>
 *   <li>{@code --key N} — rebuild the matcher for a single dataset (force).</li>
 *   <li>{@code --rebuild-all} — rebuild every published, in-scope matcher (force), equivalent to the
 *       {@code PUT /matcher/rebuild} admin endpoint.</li>
 *   <li>{@code --rebuild-stale} — rebuild only matchers that are missing or whose stored attempt is
 *       out of date (the same reconcile that runs at server startup).</li>
 *   <li>{@code --delete-small N} — remove matchers whose name count is below {@code N}.</li>
 * </ul>
 *
 * The rebuilds run asynchronously on a job executor; the command waits for them to finish before exiting.
 *
 * <p><b>Run offline only.</b> The persistent stores are not meant to be opened by two processes at once
 * (matcher start/stop is otherwise admin-API driven during blue-green deploys). Use this command with the
 * read-write server stopped, or against an instance not serving the same storage dir.
 */
public class MatcherCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(MatcherCmd.class);
  private static final String ARG_KEY = "key";
  private static final String ARG_MINIMUM = "delete-small";
  private static final String ARG_REBUILD_ALL = "rebuild-all";
  private static final String ARG_REBUILD_STALE = "rebuild-stale";
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
      .help("Rebuild the matcher for a single dataset key")
      .required(false);
    subparser.addArgument("--"+ ARG_REBUILD_ALL)
      .dest(ARG_REBUILD_ALL)
      .action(Arguments.storeTrue())
      .help("Rebuild all published, in-scope matchers (force)");
    subparser.addArgument("--"+ ARG_REBUILD_STALE)
      .dest(ARG_REBUILD_STALE)
      .action(Arguments.storeTrue())
      .help("Rebuild only missing or stale matchers");
    subparser.addArgument("--"+ ARG_MINIMUM)
       .dest(ARG_MINIMUM)
       .type(Integer.class)
       .help("Remove matchers with fewer names than this minimum")
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

      Integer key = ns.getInt(ARG_KEY);
      boolean rebuildAll = ns.getBoolean(ARG_REBUILD_ALL);
      boolean rebuildStale = ns.getBoolean(ARG_REBUILD_STALE);
      Integer min = ns.getInt(ARG_MINIMUM);

      if (key != null) {
        LOG.info("Rebuild matcher for dataset {}", key);
        matcherFactory.rebuild(key, Users.SUPERUSER);
        awaitIdle(executor);
      } else if (rebuildAll) {
        LOG.info("Rebuild ALL published matchers");
        matcherFactory.reconcile(true, Users.SUPERUSER);
        awaitIdle(executor);
      } else if (rebuildStale) {
        LOG.info("Rebuild missing or stale matchers");
        matcherFactory.reconcile(false, Users.SUPERUSER);
        awaitIdle(executor);
      } else if (min != null) {
        deleteSmallMatcher(min);
      } else {
        System.out.println("No action given. Use one of: --key <datasetKey>, --rebuild-all, --rebuild-stale, --delete-small <min>");
      }

    } finally {
      if (executor != null) {
        executor.stop();
      }
      if (matcherFactory != null) {
        matcherFactory.close();
      }
    }
  }

  /**
   * Blocks until all queued/running matcher builds have completed.
   * Builds are submitted asynchronously to the executor, so we poll its idle state.
   */
  private void awaitIdle(JobExecutor executor) throws InterruptedException {
    while (!executor.isIdle()) {
      LOG.info("Waiting for matcher builds to finish...");
      TimeUnit.SECONDS.sleep(5);
    }
    LOG.info("All matcher builds finished");
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
