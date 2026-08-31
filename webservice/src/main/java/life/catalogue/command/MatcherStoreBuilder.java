package life.catalogue.command;

import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.config.MatchingConfig;
import life.catalogue.matching.UsageMatcher;
import life.catalogue.matching.UsageMatcherFactory;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.matching.nidx.NamesIndexConfig;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds the relocatable names index + usage matcher store pair that both the DB free matching server and the
 * single release bundle ship as a self contained directory.
 *
 * Shared by {@link MatchingServerBuildCmd} and {@link BundleBuildCmd}, which differ in exactly one thing:
 * where the nidx ids come from.
 */
public class MatcherStoreBuilder {
  private static final Logger LOG = LoggerFactory.getLogger(MatcherStoreBuilder.class);

  private MatcherStoreBuilder() {}

  /**
   * @param usageFactory the database the usages of the dataset are read from
   * @param nidxFactory  the database the names index is loaded from, or null to generate nidx ids on the fly.
   *                     Pass null only when nothing else ships a names_index the ids have to agree with -
   *                     the matching server is DB free, a bundle ships its own names_index rows and must
   *                     therefore pass the very database those rows come from.
   */
  public static void build(int datasetKey, NamesIndexConfig nCfg, MatchingConfig mCfg,
                    SqlSessionFactory usageFactory, @Nullable SqlSessionFactory nidxFactory) throws Exception {
    final NameIndex ni = NameIndexFactory.build(nCfg, nidxFactory, AuthorshipNormalizer.INSTANCE);
    ni.start();
    try {
      // writes the metadata sidecar into the store dir once the store exists, so the directory can be
      // shipped as a single self contained artifact
      UsageMatcher m = UsageMatcherFactory.buildPersistentMatcher(datasetKey, mCfg, ni, usageFactory);
      m.close();
    } finally {
      ni.stop();
    }
    LOG.info("Built names index at {} and matcher store for dataset {} at {}", nCfg.file, datasetKey, mCfg.storageDir);
  }
}
