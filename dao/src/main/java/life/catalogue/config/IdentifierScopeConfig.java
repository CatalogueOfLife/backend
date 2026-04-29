package life.catalogue.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import life.catalogue.api.vocab.IdentifierScopes;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-deployment mapping from a CLB datasetKey to a scope string from the identifier scope registry.
 * The mapping is environment-dependent because datasetKeys vary across installations
 * (with the exception of the controlled COL project).
 *
 * Releases of a project all share the same identifier scheme, so callers should map
 * a release dataset to its project (sourceKey) before resolving.
 */
public class IdentifierScopeConfig {
  private static final Logger LOG = LoggerFactory.getLogger(IdentifierScopeConfig.class);

  /** scope string from {@link IdentifierScopes} -> datasetKey (or project key, never release key) */
  public BiMap<String, Integer> mapping = HashBiMap.create();

  @JsonProperty("mapping")
  public void setMapping(Map<String, Integer> m) {
    mapping = HashBiMap.create();
    m.forEach((k, v) -> {
      if (k != null && v != null) mapping.put(k, v);
    });
  }

  /**
   * Validates that all configured scopes exist in the registry. Unknown scopes are dropped with a warning,
   * unknown scope names should never be used in production. Call once after configuration is loaded.
   */
  public void validate() {
    var iter = mapping.entrySet().iterator();
    while (iter.hasNext()) {
      var e = iter.next();
      if (e.getKey() == null || e.getValue() == null || IdentifierScopes.byScope(e.getKey()) == null) {
        LOG.warn("Dropping unknown identifier scope '{}' configured for datasetKey {}", e.getKey(), e.getValue());
        iter.remove();
      } else if (!StringUtils.isAllLowerCase(e.getKey())) {
        LOG.warn("Dropping identifier scope '{}' which is not lower case", e.getKey());
        iter.remove();
      }
    }
  }
}
