package life.catalogue.matching;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.cache.UsageCacheSingleDS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class UsageMatcherMemStore implements UsageMatcherStore {
  private final Int2ObjectMap<List<String>> byCanonNidx = new Int2ObjectOpenHashMap<>();
  private final UsageCacheSingleDS usages = UsageCacheSingleDS.hashMap();

  @Override
  public List<SimpleNameClassified<SimpleNameCached>> usagesByCanonicalNidx(int nidx) {
    var canonIDs = byCanonNidx.get(nidx);
    if (canonIDs != null) {
      return canonIDs.stream()
        .map(usages::getSimpleNameClassified)
        .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  @Override
  public List<SimpleNameCached> getClassification(String usageID) throws NotFoundException {
    return usages.getClassification(usageID);
  }

  @Override
  public void add(SimpleNameCached sn) {
    if (usages.contains(sn.getId())) {
      // UPDATE
      var old = usages.get(sn.getId());
      usages.put(sn);
      if (old.getCanonicalId() != null && !Objects.equals(old.getCanonicalId(), sn.getCanonicalId())) {
        byCanonNidx.get(old.getCanonicalId()).remove(old.getId());
        byCanonNidx.computeIfAbsent(sn.getCanonicalId(), k -> new ArrayList<>()).add(sn.getId());
      }
    } else {
      usages.put(sn);
      if (sn.getCanonicalId() != null) {
        byCanonNidx.computeIfAbsent(sn.getCanonicalId(), k -> new ArrayList<>()).add(sn.getId());
      }
    }
  }

  @Override
  public void updateUsageID(String oldID, String newID) {
    var old = usages.get(oldID);
    old.setId(newID);
    add(old);
    // remove former id
    usages.remove(oldID);
    if (old.getCanonicalId() != null){
      var ids = byCanonNidx.get(old.getCanonicalId());
      ids.remove(oldID);
      ids.add(newID);
    }
    // update other usages pointing to the old id via parentID
    usages.updateParent(oldID, newID);
  }

  @Override
  public void updateParentId(String usageID, String parentId) {
    var old = usages.get(usageID);
    old.setParent(parentId);
    add(old);
  }

}
