package life.catalogue.importer.store;

import life.catalogue.api.model.NameRelation;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.common.kryo.map.MapDbObjectSerializer;
import life.catalogue.common.kryo.map.MapDbStringArraySerializer;
import life.catalogue.common.kryo.map.MapDbStringSetSerializer;
import life.catalogue.importer.IdGenerator;
import life.catalogue.importer.store.model.NameData;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import life.catalogue.importer.store.model.RelationData;

import org.apache.commons.lang3.ArrayUtils;
import org.mapdb.DB;
import org.mapdb.Serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;

public class NameStore extends CRUDStore<NameData> {
  
  // scientificName to nameID
  private final Map<String, Set<String>> names;

  public NameStore(DB mapDb, String mapDbName, Pool<Kryo> pool, IdGenerator idGen, ImportStore importStore) throws IOException {
    super(mapDb, mapDbName, NameData.class, pool, importStore, idGen);
    names = mapDb.hashMap(mapDbName+"-names")
        .keySerializer(Serializer.STRING)
        .valueSerializer(new MapDbStringSetSerializer(pool, 64))
        .createOrOpen();
  }
  
  /**
   * @return the matching name nodes with the scientificName in a mutable set
   */
  public Set<String> nameIdsByName(String scientificName) {
    return names.getOrDefault(scientificName, new HashSet<>());
  }
  
  @Override
  public boolean create(NameData obj) {
    boolean created = super.create(obj);
    if (created) {
      addToIndex(obj);
    }
    return created;
  }
  
  @Override
  public NameData update(NameData obj) {
    var old = super.update(obj);
    if (old != null) {
      rmFromIndex(old);
      addToIndex(obj);
    }
    return old;
  }
  
  @Override
  public NameData remove(String id) {
    NameData nn = super.remove(id);
    if (nn != null) {
      rmFromIndex(nn);
    }
    return nn;
  }
  
  private void rmFromIndex(NameData n) {
    if (n.getName().getScientificName() != null) {
      var nids = names.get(n.getName().getScientificName());
      if (nids != null) {
        nids.remove(n.getId());
        if (nids.isEmpty()) {
          names.remove(n.getName().getScientificName());
        } else {
          names.put(n.getName().getScientificName(), nids);
        }
      }
    }
  }
  
  private void addToIndex(NameData n) {
    if (n.getName().getScientificName() != null) {
      var sciname = n.getName().getScientificName();
      var nids = names.getOrDefault(sciname, new HashSet<>());
      nids.add(n.getId());
      names.put(sciname, nids);
    }
  }

  public List<NameRelation> relations(String nameID) {
    return objByID(nameID).relations.stream()
      .map(RelationData::toNameRelation)
      .toList();
  }
}
