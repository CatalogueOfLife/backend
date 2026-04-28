package life.catalogue.matching;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.IdentifierScopes;
import life.catalogue.config.IdentifierScopeConfig;
import life.catalogue.db.mapper.DatasetMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Resolves the identifier scope string for a CLB dataset using {@link IdentifierScopeConfig}.
 *
 * Releases of a project share the same identifier scheme as the project, so a release dataset
 * is mapped to its project (sourceKey) before lookup.
 */
public class IdentifierScopeResolver {

  private final IdentifierScopeConfig cfg;
  private final SqlSessionFactory factory;

  public IdentifierScopeResolver(IdentifierScopeConfig cfg, SqlSessionFactory factory) {
    this.cfg = cfg;
    this.factory = factory;
  }

  /**
   * @return the effective project-level dataset key for scope lookups: the sourceKey if set
   *         (the dataset is a release), otherwise the datasetKey itself.
   */
  public static int effectiveKey(int datasetKey, Integer sourceKey) {
    return sourceKey != null ? sourceKey : datasetKey;
  }

  /**
   * @return the configured scope string for the given dataset, or null if no mapping exists.
   *         The returned string is guaranteed to exist in {@link IdentifierScopes}.
   */
  public String resolve(int datasetKey, Integer sourceKey) {
    return cfg.mapping.get(effectiveKey(datasetKey, sourceKey));
  }

  /** Convenience for already-loaded Dataset. */
  public String resolve(Dataset d) {
    if (d == null) return null;
    return resolve(d.getKey(), d.getSourceKey());
  }

  /**
   * DB-lookup variant for callers that only have a datasetKey.
   * @return the configured scope string, or null if unmapped or the dataset does not exist.
   */
  public String resolve(int datasetKey) {
    // first try the simple case: the key itself is mapped (covers projects and external datasets)
    String scope = cfg.mapping.get(datasetKey);
    if (scope != null) return scope;
    // otherwise look up the dataset to find a possible sourceKey for a release
    try (SqlSession session = factory.openSession()) {
      Dataset d = session.getMapper(DatasetMapper.class).get(datasetKey);
      if (d == null || d.getSourceKey() == null) return null;
      return cfg.mapping.get(d.getSourceKey());
    }
  }
}
