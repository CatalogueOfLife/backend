package life.catalogue.exporter;

import life.catalogue.db.mapper.NameUsageMapper;

import java.util.*;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import com.google.common.base.Preconditions;

public class NameUsageKeyMap {
  private final Map<String, String> name2usageID = new HashMap<>();
  // a new map that can hold list of usageIDs in case there are more than one
  // which is very rare, so we prefer to reduce list instances in a second map
  private final Map<String, Set<String>> name2usageIDExtras = new HashMap<>();
  /**
   * Reverse index over the values of name2usageID, which used to be answered by a linear containsValue scan
   * over one entry per usage - quadratic once a large dataset exported its bare names.
   * It is one more entry per usage, so it is only kept when something actually asks the question.
   */
  private final Set<String> usageIDs;
  /**
   * Name ids known to have no usage at all, i.e. bare names. Without this every relation pointing at one
   * re-ran the same fruitless query, because a miss used to leave no trace in either map.
   */
  private final Set<String> bareNameIDs = new HashSet<>();
  /**
   * A factory rather than a session: the map outlives any single export pass, and the session it used to
   * borrow was one the export held open for its whole run. Lookups are rare now and remembered either way.
   */
  private final SqlSessionFactory factory;
  private final int datasetKey;

  public NameUsageKeyMap(int datasetKey, SqlSessionFactory factory) {
    this(datasetKey, factory, true);
  }

  /**
   * @param trackUsageIDs whether to maintain the reverse index that backs {@link #containsUsageID(String)}
   */
  public NameUsageKeyMap(int datasetKey, SqlSessionFactory factory, boolean trackUsageIDs) {
    this.datasetKey = datasetKey;
    this.factory = factory;
    usageIDs = trackUsageIDs ? new HashSet<>() : null;
  }

  private Set<String> load(String nameID) {
    if (bareNameIDs.contains(nameID)) {
      return Collections.emptySet();
    }
    final List<String> uids;
    try (SqlSession session = factory.openSession()) {
      uids = session.getMapper(NameUsageMapper.class).listUsageIDsByNameID(datasetKey, nameID);
    }
    if (uids != null && !uids.isEmpty()) {
      Set<String> uidSet = new HashSet<>(uids);
      add(nameID, uids.remove(0));
      if (!uids.isEmpty()) {
        name2usageIDExtras.put(nameID, new HashSet<>(uids));
        if (usageIDs != null) {
          usageIDs.addAll(uids);
        }
      }
      return uidSet;
    }
    bareNameIDs.add(nameID);
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
    if (usageIDs != null) {
      usageIDs.add(usageID);
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
    Preconditions.checkState(usageIDs != null, "usage ids are not tracked by this map");
    return usageIDs.contains(usageID);
  }

  public String getFirst(String nameId) {
    if (!name2usageID.containsKey(nameId)) {
      load(nameId);
    }
    return name2usageID.get(nameId);
  }
}
