package life.catalogue.importer.store;

import com.google.common.base.Function;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.VerbatimEntity;
import life.catalogue.api.vocab.Issue;
import life.catalogue.common.kryo.map.MapDbObjectSerializer;
import life.catalogue.importer.IdGenerator;
import life.catalogue.importer.store.model.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.mapdb.DB;
import org.mapdb.Serializer;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;

public abstract class CRUDStore<T extends DSID<String> & VerbatimEntity> {
  private static final Logger LOG = LoggerFactory.getLogger(CRUDStore.class);
  // ID -> obj
  protected final Map<String, T> objects;

  private int duplicateCounter = 0;
  private final IdGenerator idGen;
  private final String objName;
  protected final ImportStore db;

  CRUDStore(DB mapDb, String mapDbName, Class<T> clazz, Pool<Kryo> pool, ImportStore db, IdGenerator idGen) throws IOException {
    this.db = db;
    objName = clazz.getSimpleName();
    objects = mapDb.hashMap(mapDbName)
        .keySerializer(Serializer.STRING)
        .valueSerializer(new MapDbObjectSerializer<>(clazz, pool, 256))
        .counterEnable()
        .createOrOpen();
    this.idGen = idGen;
  }
  
  public void logKeys() {
    objects.keySet().stream().sorted().forEach(key -> System.out.println(key));
  }

  public boolean exists(String id) {
    return id != null && objects.containsKey(id);
  }

  public T objByID(String id) {
    if (id != null) {
      return objects.get(id);
    }
    return null;
  }
  
  public Stream<T> all() {
    return objects.values().stream();
  }

  public Stream<String> allKeys() {
    return objects.keySet().stream();
  }

  /**
   * @return a stream of all unique ids
   */
  public Stream<String> allIds() {
    return objects.keySet().stream();
  }

  /**
   * Creates a new object and returns true if the object was persisted.
   * False is returned when an object with the same ID already existed.
   */
  public boolean create(T obj) {
    // create missing ids, sharing the same id between name & taxon
    if (obj.getId() == null) {
      obj.setId(idGen.next());
      LOG.debug("Generate new {} ID: {}", obj.getClass().getSimpleName(), obj.getId());
    }
  
    // assert ID is unique
    if (objects.containsKey(obj.getId())) {
      LOG.warn("Duplicate {}ID {}", objName, obj.getId());
      duplicateCounter++;
      db.addIssues(obj, Issue.ID_NOT_UNIQUE);
      T obj2 = objByID(obj.getId());
      db.addIssues(obj2, Issue.ID_NOT_UNIQUE);
      return false;
    }

    objects.put(obj.getId(), obj);
    return true;
  }

  /**
   * Updates the object in the store.
   * Returns the old object if it existed, null otherwise.
   */
  public T update(T obj) {
    if (objects.containsKey(obj.getId())) {
      return objects.put(obj.getId(), obj);
    }
    return null;
  }
  
  /**
   * Removes the object with all its relations and all entities stored under this instance.
   */
  public T remove(String id) {
    T obj = objects.remove(id);
    if (obj != null) {
      //TODO: remove all relations as well
    }
    return obj;
  }

  public <X extends Enum<?>> void removeRelation(RelationData<X> r, Function<T, List<RelationData<X>>> getRelsFrom, Function<T, List<RelationData<X>>> getRelsTo) {
    var from = objects.get(r.getFromID());
    if (from != null) {
      if (getRelsFrom.apply(from).remove(r)) {
        objects.put(from.getId(), from);
      }
    }

    if (r.getToID() != null) {
      var to = objects.get(r.getToID());
      if (to != null) {
        if (getRelsTo.apply(to).remove(r)) {
          objects.put(to.getId(), to);
        }
      }
    }
  }

  public int getDuplicateCounter() {
    return duplicateCounter;
  }

  public int size() {
    return objects.size();
  }
}
