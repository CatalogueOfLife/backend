package life.catalogue.exporter;

import life.catalogue.db.mapper.NameUsageMapper;

import java.util.*;

import org.apache.ibatis.session.SqlSession;

public class NameUsageKeyMap {
  private final Map<String, String> name2usageID = new HashMap<>();
  // a new map that can hold list of usageIDs in case there are more than one
  // which is very rare, so we prefer to reduce list instances in a second map
  private final Map<String, Set<String>> name2usageIDExtras = new HashMap<>();
  private final NameUsageMapper usageMapper;
  private final int datasetKey;

  public NameUsageKeyMap(int datasetKey, SqlSession session) {
    this.datasetKey = datasetKey;
    usageMapper = session.getMapper(NameUsageMapper.class);
  }

  private Set<String> load(String nameID) {
    List<String> uids = usageMapper.listUsageIDsByNameID(datasetKey, nameID);
    if (uids != null && !uids.isEmpty()) {
      Set<String> uidSet = new HashSet<>(uids);
      name2usageID.put(nameID, uids.remove(0));
      if (!uids.isEmpty()) {
        name2usageIDExtras.put(nameID, new HashSet<>(uids));
      }
      return uidSet;
    }
    return Collections.emptySet();
  }

  public void add(String nameID, String usageID) {
    if (name2usageID.containsKey(nameID)) {
      if (!name2usageID.get(nameID).equals(usageID)) {
        if (!name2usageIDExtras.containsKey(nameID)) {
          name2usageIDExtras.put(nameID, new HashSet<>());
        }
        name2usageIDExtras.get(nameID).add(usageID);
      }
    } else {
      name2usageID.put(nameID, usageID);
    }
  }

  public Set<String> usageIDs(String nameID) {
    if (name2usageIDExtras.containsKey(nameID)) {
      Set<String> uids = name2usageIDExtras.get(nameID);
      uids.add(name2usageID.get(nameID));
      return uids;

    } else if (name2usageID.containsKey(nameID)) {
      return Set.of(name2usageID.get(nameID));

    } else {
      return load(nameID);
    }
  }

  public boolean containsNameID(String nameID) {
    return name2usageID.containsKey(nameID);
  }

  public boolean containsUsageID(String usageID) {
    return name2usageID.containsValue(usageID);
  }

  public String getFirst(String nameId) {
    if (!name2usageID.containsKey(nameId)) {
      load(nameId);
    }
    return name2usageID.get(nameId);
  }
}
