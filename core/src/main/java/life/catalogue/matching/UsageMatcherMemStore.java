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
  public List<SimpleNameClassified<SimpleNameCached>> usagesByCanonicalNidx(int nidx) {
    var canonIDs = byCanonNidx.get(nidx);
    if (canonIDs != null) {
      return canonIDs.stream()
        .map(this::getSNClassified)
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
        byCanonNidx.computeIfAbsent(sn.getCanonicalId(), k -> new HashSet<>()).add(sn.getId());
      }
    } else {
      if (sn.getCanonicalId() != null) {
        byCanonNidx.computeIfAbsent(sn.getCanonicalId(), k -> new HashSet<>()).add(sn.getId());
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
