package life.catalogue.matching;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;

import java.util.*;
import java.util.stream.Collectors;

public class UsageMatcherMemStore extends UsageMatcherAbstractStore {
  private final Map<Integer, Set<String>> byCanonNidx;

  public UsageMatcherMemStore(int datasetKey) {
    super(datasetKey, new HashMap<>(), false);
    this.byCanonNidx = new Int2ObjectOpenHashMap<>();
  }

  @Override
  public Collection<Integer> allCanonicalIds() {
    return byCanonNidx.keySet();
  }

  @Override
  public List<SimpleNameClassified<SimpleNameCached>> usagesByCanonicalId(int canonId) {
    var canonIDs = byCanonNidx.get(canonId);
    if (canonIDs != null) {
      return canonIDs.stream()
        .map(this::getSNClassified)
        .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  @Override
  public List<SimpleNameCached> simpleNamesByCanonicalId(int canonId) {
    var canonIDs = byCanonNidx.get(canonId);
    if (canonIDs != null) {
      return canonIDs.stream()
        .map(this::get)
        .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  @Override
  public void add(SimpleNameCached sn) {
    var old = usages.put(sn.getId(), sn);
    if (old != null) {
      // UPDATE
      if (Objects.equals(old.getCanonicalId(), sn.getCanonicalId())) {
        if (!old.getId().equals(sn.getId())) {
          var ids = byCanonNidx.get(old.getCanonicalId());
          ids.remove(old.getId());
          ids.add(sn.getId());
        }
      } else {
        byCanonNidx.get(old.getCanonicalId()).remove(old.getId());
        add2Canon(sn.getCanonicalId(), sn.getId());
      }
    } else {
      if (sn.getCanonicalId() != null) {
        add2Canon(sn.getCanonicalId(), sn.getId());
      }
    }
  }

  /** Adds an id to a canonical bucket, honouring {@link #MAX_USAGES_PER_CANONICAL} like the chronicle store does. */
  private void add2Canon(Integer canonicalId, String usageID) {
    var ids = byCanonNidx.computeIfAbsent(canonicalId, k -> new HashSet<>());
    if (ids.size() < MAX_USAGES_PER_CANONICAL) {
      ids.add(usageID);
      if (ids.size() == MAX_USAGES_PER_CANONICAL) {
        LOG.warn("Canonical nidx {} of dataset {} hit the cap of {} usages. Further usages are not indexed as match candidates for it",
          canonicalId, datasetKey(), MAX_USAGES_PER_CANONICAL);
      }
    }
  }

  @Override
  protected void replaceByCanonUsageID(Integer canonicalId, String oldID, String newID) {
    var ids = byCanonNidx.get(canonicalId);
    ids.remove(oldID);
    ids.add(newID);
  }

  @Override
  public void close() {
    // nothing to close
  }
}
