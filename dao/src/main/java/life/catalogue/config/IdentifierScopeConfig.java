package life.catalogue.config;

import life.catalogue.api.vocab.IdentifierScopes;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.constraints.NotNull;

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

  /** datasetKey (project, not release) -> scope string from {@link IdentifierScopes}. */
  @NotNull
  public Map<Integer, String> mapping = new HashMap<>();

  /**
   * Validates that all configured scopes exist in the registry. Unknown scopes are dropped with a warning,
   * unknown scope names should never be used in production. Call once after configuration is loaded.
   */
  public void validate() {
    var iter = mapping.entrySet().iterator();
    while (iter.hasNext()) {
      var e = iter.next();
      if (e.getValue() == null || IdentifierScopes.byScope(e.getValue()) == null) {
        LOG.warn("Dropping unknown identifier scope '{}' configured for datasetKey {}", e.getValue(), e.getKey());
        iter.remove();
      } else {
        // normalise to the canonical lowercase scope string
        e.setValue(IdentifierScopes.byScope(e.getValue()).getScope());
      }
    }
  }
}
