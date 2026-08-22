package life.catalogue.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameUsage;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.vocab.TaxGroup;

import java.util.*;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * A heap based, fully mutable store used while data is still changing - the extended release merges into
 * one of these. There is no fan out limit: a canonical bucket is a plain set.
 */
public class UsageMatcherMemStore implements UsageMatcherStore {
  private final int datasetKey;
  private final Map<String, SimpleNameCached> usages = new HashMap<>();
  private final Map<Integer, Set<String>> byCanonNidx = new Int2ObjectOpenHashMap<>();

  public UsageMatcherMemStore(int datasetKey) {
    this.datasetKey = datasetKey;
  }

  @Override
  public int datasetKey() {
    return datasetKey;
  }

  @Override
  public int size() {
    return usages.size();
  }

  @Override
  public int canonicalSize() {
    return byCanonNidx.size();
  }

  @Override
  public Collection<Integer> allCanonicalIds() {
    return byCanonNidx.keySet();
  }

  @Override
  public List<SimpleNameCached> simpleNamesByCanonicalId(int canonId) {
    var canonIDs = byCanonNidx.get(canonId);
    if (canonIDs == null) return List.of();
    var list = new ArrayList<SimpleNameCached>(canonIDs.size());
    for (var id : canonIDs) {
      list.add(get(id));
    }
    return list;
  }

  @Override
  public SimpleNameCached get(String usageID) throws NotFoundException {
    var sn = usages.get(usageID);
    if (sn == null) {
      throw NotFoundException.notFound(NameUsage.class, DSID.of(datasetKey, usageID));
    }
    return sn;
  }

  @Override
  public Iterable<SimpleNameCached> all() {
    return usages.values();
  }

  @Override
  public void update(String usageID, TaxGroup group) {
    get(usageID).setGroup(group);
  }

  @Override
  public void add(SimpleNameCached sn) {
    var old = usages.put(sn.getId(), sn);
    if (old != null && !Objects.equals(old.getCanonicalId(), sn.getCanonicalId())) {
      // the usage moved to a different canonical name, drop it from the old bucket
      if (old.getCanonicalId() != null) {
        var ids = byCanonNidx.get(old.getCanonicalId());
        if (ids != null) {
          ids.remove(old.getId());
        }
      }
      old = null; // fall through to the insert below
    }
    if (old == null && sn.getCanonicalId() != null) {
      byCanonNidx.computeIfAbsent(sn.getCanonicalId(), k -> new HashSet<>()).add(sn.getId());
    }
  }

  @Override
  public void updateParentId(String usageID, String parentId) {
    get(usageID).setParent(parentId);
  }

  @Override
  public void close() {
    // nothing to close
  }
}
