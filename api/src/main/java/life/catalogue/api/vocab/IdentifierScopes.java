package life.catalogue.api.vocab;

import life.catalogue.api.model.Identifier;
import life.catalogue.common.io.Resources;
import life.catalogue.common.util.YamlUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;

/**
 * Curated registry of identifier scopes, loaded once from the bundled YAML file at
 * {@link #RESOURCE}. The registry is the source of truth for which CURIE-style
 * scopes exist and what metadata (title, link, resolver, etc.) they carry.
 *
 * The {@link Identifier.Scope} enum is a typed shortcut for well-known scopes;
 * every enum value MUST have a matching registry entry (asserted by tests).
 */
public final class IdentifierScopes {

  public static final String RESOURCE = "vocab/identifier-scopes/identifier-scopes.yaml";

  /** All scopes keyed by their lowercased scope string. Immutable. */
  public static final Map<String, IdentifierScope> SCOPES;

  /** All scopes keyed by their CLB datasetKey. Immutable. Only entries with a datasetKey are included. */
  public static final Map<Integer, IdentifierScope> BY_DATASET_KEY;

  static {
    Map<String, IdentifierScope> byScope = new HashMap<>();
    Map<Integer, IdentifierScope> byKey = new HashMap<>();
    for (IdentifierScope s : load()) {
      if (s.getScope() == null) {
        throw new IllegalStateException("Identifier scope entry without a scope: " + s);
      }
      String key = s.getScope().toLowerCase();
      if (byScope.put(key, s) != null) {
        throw new IllegalStateException("Duplicate identifier scope: " + key);
      }
      if (s.getDatasetKey() != null && byKey.put(s.getDatasetKey(), s) != null) {
        throw new IllegalStateException("Duplicate datasetKey in identifier scope registry: " + s.getDatasetKey());
      }
    }
    SCOPES = ImmutableMap.copyOf(byScope);
    BY_DATASET_KEY = ImmutableMap.copyOf(byKey);
  }

  private IdentifierScopes() {}

  private static List<IdentifierScope> load() {
    try {
      return YamlUtils.read(new TypeReference<List<IdentifierScope>>() {}, Resources.stream(RESOURCE));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load identifier scope registry from " + RESOURCE, e);
    }
  }

  /** @return the scope entry for the given scope string (case-insensitive), or null if unknown. */
  public static IdentifierScope byScope(String scope) {
    if (scope == null) return null;
    return SCOPES.get(scope.toLowerCase().trim());
  }

  /** @return the scope entry for the given CLB datasetKey, or null if not registered. */
  public static IdentifierScope byDatasetKey(int datasetKey) {
    return BY_DATASET_KEY.get(datasetKey);
  }

  /** @return all registered scopes in load order. */
  public static Collection<IdentifierScope> all() {
    return SCOPES.values();
  }
}
