package life.catalogue.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.IdentifierScopes;
import life.catalogue.common.collection.CollectionUtils;
import life.catalogue.config.IdentifierScopeConfig;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.mapper.DatasetMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import static life.catalogue.api.util.ObjectUtils.coalesce;

/**
 * Resolves the identifier scope string for a CLB dataset using {@link IdentifierScopeConfig}.
 *
 * Releases of a project share the same identifier scheme as the project, so a release dataset
 * is mapped to its project (sourceKey) before lookup.
 */
public class IdentifierScopeResolver {

  private final IdentifierScopeConfig cfg;

  public IdentifierScopeResolver(IdentifierScopeConfig cfg) {
    this.cfg = cfg;
  }

  /** Convenience for already-loaded Dataset. */
  public String resolve(Dataset d) {
    if (d == null) return null;
    return cfg.mapping.inverse().get(coalesce(d.getSourceKey(), d.getKey()));
  }

  /**
   * DB-lookup variant for callers that only have a datasetKey.
   * @return the configured scope string, or null if unmapped or the dataset does not exist.
   */
  public String resolve(int datasetKey) {
    // first try the simple case: the key itself is mapped (covers projects and external datasets)
    String scope = cfg.mapping.inverse().get(datasetKey);
    if (scope != null) return scope;
    // otherwise look up the dataset to find a possible sourceKey for a release
    try {
      var info = DatasetInfoCache.CACHE.info(datasetKey);
      if (info != null && info.sourceKey != null) {
        return cfg.mapping.inverse().get(info.sourceKey);
      }
    } catch (NotFoundException e) {
      // swallow, we return null
    }
    return null;
  }
}
