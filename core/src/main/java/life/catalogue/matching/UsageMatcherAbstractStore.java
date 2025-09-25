package life.catalogue.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameUsage;
import life.catalogue.api.model.SimpleNameCached;

import java.util.*;

public abstract class UsageMatcherAbstractStore implements UsageMatcherStore {
  protected final Map<String, SimpleNameCached> usages;
  private final int datasetKey;
  private final boolean immutableMap;

  protected UsageMatcherAbstractStore(int datasetKey, Map<String, SimpleNameCached> usages, boolean immutableMap) {
    this.usages = usages;
    this.datasetKey = datasetKey;
    this.immutableMap = immutableMap;
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
  public SimpleNameCached get(String usageID) throws NotFoundException {
    var sn = usages.get(usageID);
    if (sn == null) {
      throw NotFoundException.notFound(NameUsage.class, DSID.of(datasetKey, usageID));
    }
    return sn;
  }

  @Override
  public void updateParentId(String usageID, String parentId) {
    var old = usages.get(usageID);
    old.setParent(parentId);
    add(old);
  }

  @Override
  public void updateUsageID(String oldID, String newID) {
    var obj = usages.remove(oldID);
    obj.setId(newID);
    usages.put(newID, obj);
    if (obj.getCanonicalId() != null){
      replaceByCanonUsageID(obj.getCanonicalId(), oldID, newID);
    }
    // update other usages pointing to the old id via parentID
    for (var sn : usages.values()) {
      if (Objects.equals(oldID, sn.getParent())) {
        sn.setParent(newID);
        if (immutableMap) {
          // we need to reinsert the updated object
          usages.put(sn.getId(), sn);
        }
      }
    }
  }

  protected abstract void replaceByCanonUsageID(Integer canonicalId, String oldID, String newID);
}
