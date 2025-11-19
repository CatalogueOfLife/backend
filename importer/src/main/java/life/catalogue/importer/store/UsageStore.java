package life.catalogue.importer.store;

import life.catalogue.importer.IdGenerator;
import life.catalogue.importer.store.model.RankedUsage;
import life.catalogue.importer.store.model.UsageData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gbif.nameparser.api.Rank;

import org.mapdb.DB;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;
import com.google.common.base.Preconditions;

import javax.annotation.Nullable;

public class UsageStore extends CRUDStore<UsageData> {

  public UsageStore(DB mapDb, String mapDbName, Pool<Kryo> pool, IdGenerator idGen, ImportStore importStore) throws IOException {
    super(mapDb, mapDbName, UsageData.class, pool, importStore, idGen);
  }

  public Stream<UsageData> allTaxa() {
    return all().filter(UsageData::isTaxon);
  }
  public Stream<UsageData> allSynonyms() {
    return all().filter(UsageData::isSynonym);
  }
  public Stream<UsageData> allBareNames() {
    return all().filter(UsageData::isBareName);
  }

  @Override
  public boolean create(UsageData obj) {
    if (obj.nameID == null && obj.usage.getName() != null && obj.usage.getName().getId() != null) {
      obj.nameID = obj.usage.getName().getId();
    }
    Preconditions.checkNotNull(obj.nameID, "Usage requires an existing nameID");
    obj.usage.setName(null); // do not persist name object
    var created = super.create(obj);
    if (created) {
      var n = importStore.names().objByID(obj.nameID);
      n.usageIDs.add(obj.getId());
    }
    return created;
  }

  @Override
  public UsageData update(UsageData obj) {
    obj.usage.setName(null); // do not persist name object
    var old = super.update(obj);
    if (old != null && !Objects.equals(old.nameID, obj.nameID)) {
      // add to new name
      var n = importStore.names().objByID(obj.nameID);
      n.usageIDs.add(obj.getId());
      // remove from old name
      n = importStore.names().objByID(old.nameID);
      n.usageIDs.remove(old.getId());
    }
    return old;
  }

  @Override
  public UsageData remove(String id) {
    var u = super.remove(id);
    if (u != null) {
      var n = importStore.names().objByID(u.nameID);
      n.usageIDs.remove(u.getId());
    }
    return u;
  }

  public List<UsageData> listRoot() {
    return objects.values().stream()
        .filter(u -> u.usage.getParentId() == null && u.usage.isTaxon())
        .collect(Collectors.toList());
  }

  public void assignParent(String usageID, String newParentID) {
    assignParent(objByID(usageID), newParentID);
  }
  public void assignParent(UsageData u, String newParentID) {
    // avoid self referencing loops
    if (newParentID != null && !newParentID.equals(u.getId())) {
      u.usage.asUsageBase().setParentId(newParentID);
      super.update(u);
    }
  }

  /**
   * List all accepted taxa of a potentially prop parte synonym
   */
  public List<UsageData> accepted(String synonymID) {
    var u = objByID(synonymID);
    return accepted(u);
  }
  /**
   * List all accepted taxa of a potentially prop parte synonym
   */
  public List<UsageData> accepted(UsageData syn) {
    if (syn.usage.isTaxon()) {
      return null;
    }
    var pIDs = new HashSet<String>();
    pIDs.add(syn.usage.getParentId());
    pIDs.addAll(syn.proParteAcceptedIDs);
    return pIDs.stream()
      .map(this::objByID)
      .collect(Collectors.toList());
  }

  public UsageData parent(String parentID) {
    return objByID(parentID);
  }
  public UsageData parent(UsageData child) {
    return parent(child.usage.getParentId());
  }
  public UsageData parent(UsageData child, Rank parentRank) {
    if (child.usage.getParentId() != null) {
      var p = objByID(child.usage.getParentId());
      if (p.usage.getRank() == parentRank) {
        return p;
      }
      return parent(p, parentRank);
    }
    return null;
  }

  /**
   * List all parents excluding the given startID.
   */
  public List<UsageData> parents(String startID) {
    return parents(objByID(startID));
  }

  public List<UsageData> parents(UsageData child) {
    return collectParents(new ArrayList<>(), child, null);
  }

  private List<UsageData> collectParents(List<UsageData> parents, UsageData u, @Nullable String stopID) {
    if (u.usage.getParentId() != null && (stopID == null || !u.usage.getId().equals(stopID))) {
      var pu = objByID(u.usage.getParentId());
      parents.add(pu);
      collectParents(parents, pu, stopID);
    }
    return parents;
  }
  public List<UsageData> parentsUntil(UsageData child, String untilID) {
    return collectParents(new ArrayList<>(), child, untilID);
  }

  /**
   * Ineffective method
   * @param accID
   * @return
   */
  public List<UsageData> synonymsOf(String accID) {
    return allSynonyms()
      .filter(u -> u.usage.getParentId() != null && u.usage.getParentId().equals(accID))
      .collect(Collectors.toList());
  }
}
