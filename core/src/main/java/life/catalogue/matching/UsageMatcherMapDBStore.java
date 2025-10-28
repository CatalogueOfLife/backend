package life.catalogue.matching;

import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.mapdb.serializer.SerializerArray;

public class UsageMatcherMapDBStore extends UsageMatcherAbstractStore {
  private final Map<Integer, String[]> byCanonNidx;
  private final DB db;

  public static UsageMatcherMapDBStore build(int datasetKey, DB db) {
    var usages = db.hashMap("usages")
      .keySerializer(Serializer.STRING)
      .valueSerializer(new MapDbStorageSerializer())
      .createOrOpen();
    var byCanonNidx = db.hashMap("canon")
      .keySerializer(Serializer.INTEGER)
      .valueSerializer(new SerializerArray<>(Serializer.STRING, String.class))
      .createOrOpen();
    return new UsageMatcherMapDBStore(datasetKey, db, usages, byCanonNidx);
  }

  private UsageMatcherMapDBStore(int datasetKey, DB db, Map<String, SimpleNameCached> usages, Map<Integer, String[]> byCanonNidx) {
    super(datasetKey, usages, true);
    this.byCanonNidx = byCanonNidx;
    this.db = db;
  }

  @Override
  public void close() {
    db.close();
  }

  @Override
  public List<SimpleNameClassified<SimpleNameCached>> usagesByCanonicalId(int canonId) {
    var canonIDs = byCanonNidx.get(canonId);
    if (canonIDs != null) {
      return Arrays.stream(canonIDs)
        .map(this::getSNClassified)
        .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  @Override
  public List<SimpleNameCached> simpleNamesByCanonicalId(int canonId) {
    var canonIDs = byCanonNidx.get(canonId);
    if (canonIDs != null) {
      return Arrays.stream(canonIDs)
        .map(this::get)
        .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  @Override
  public void add(SimpleNameCached sn) {
    var old = usages.put(sn.getId(), sn);
    if (sn.getCanonicalId() != null) {
      if (old != null) {
        // UPDATE
        if (!Objects.equals(old.getCanonicalId(), sn.getCanonicalId())) {
          // remove from old array
          var ids = byCanonNidx.get(old.getCanonicalId());
          int idx = ArrayUtils.indexOf(ids, old.getId());
          byCanonNidx.put(old.getCanonicalId(), ArrayUtils.remove(ids, idx));
          // add to new array
          add2Canon(sn.getCanonicalId(), sn);
        } else {
          // nothing - same canonicalID and same sn.ID
        }
      } else {
        add2Canon(sn.getCanonicalId(), sn);
      }
    }
  }

  private void add2Canon(Integer canonicalId, SimpleNameCached sn) {
    if (byCanonNidx.containsKey(canonicalId)) {
      var ids = byCanonNidx.get(canonicalId);
      byCanonNidx.put(canonicalId, ArrayUtils.add(ids, sn.getId()));
    } else {
      byCanonNidx.put(canonicalId, new String[]{sn.getId()});
    }
  }

  @Override
  protected void replaceByCanonUsageID(Integer canonicalId, String oldID, String newID) {
    var ids = byCanonNidx.get(canonicalId);
    var idx = ArrayUtils.indexOf(ids, oldID);
    ids[idx] = newID;
  }

  @Override
  public Collection<Integer> allCanonicalIds() {
    return byCanonNidx.keySet();
  }

}
