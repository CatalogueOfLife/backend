package org.col.admin.importer.neo;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import com.esotericsoftware.kryo.pool.KryoPool;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.ArrayUtils;
import org.col.admin.importer.IdGenerator;
import org.col.admin.importer.neo.model.NeoNode;
import org.col.api.model.VerbatimEntity;
import org.col.api.model.VerbatimID;
import org.col.api.vocab.Issue;
import org.col.common.mapdb.MapDbObjectSerializer;
import org.mapdb.DB;
import org.mapdb.Serializer;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.kernel.impl.core.NodeProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeoCRUDStore<T extends VerbatimID & NeoNode> {
  private static final Logger LOG = LoggerFactory.getLogger(NeoCRUDStore.class);
  // nodeId -> obj
  private final Map<Long, T> objects;
  // ID -> nodeId
  private final Map<String, Long> ids;
  private int duplicateCounter = 0;
  private final IdGenerator idGen;
  protected final GraphDatabaseService neo;
  private final String objName;
  private final BiConsumer<VerbatimEntity, Issue> addIssueFunc;
  private final BiFunction<Map<String,Object>, Label[], Node> createNode;
  
  
  NeoCRUDStore(GraphDatabaseService neo,
                      DB mapDb, String mapDbName, Class<T> clazz, KryoPool pool,
                      IdGenerator idGen,
                      BiConsumer<VerbatimEntity, Issue> addIssueFunc,
                      BiFunction<Map<String,Object>, Label[], Node> createNode) {
    this.neo = neo;
    objName = clazz.getSimpleName();
    objects = mapDb.hashMap(mapDbName)
        .keySerializer(Serializer.LONG)
        .valueSerializer(new MapDbObjectSerializer<>(clazz, pool, 256))
        .counterEnable()
        .createOrOpen();
    ids = mapDb.hashMap(mapDbName+"-ids")
        .keySerializer(Serializer.STRING)
        .valueSerializer(Serializer.LONG)
        .createOrOpen();
    this.idGen = idGen;
    this.addIssueFunc = addIssueFunc;
    this.createNode = createNode;
  }
  
  public T objByNode(Node n) {
    T t = objects.get(n.getId());
    if (t != null) {
      t.setNode(n);
    }
    return t;
  }
  
  public T objByID(String id) {
    if (id != null) {
      Node n = nodeByID(id);
      if (n != null) {
        return objByNode(n);
      }
    }
    return null;
  }

  public Node nodeByID(String id) {
    Long nodeId = ids.getOrDefault(id, null);
    return nodeId == null ? null : neo.getNodeById(nodeId);
  }
  
  /**
   * Return all NeoUsage incl a node property to work with the nodeId.
   * Note though that it is not a real neo4j node but just a dummy that contains the id!!!
   * No other neo operations can be done on this node - it would need to be retrieved from the store
   * individually.
   */
  public Stream<T> all() {
    return objects.entrySet().stream().map(e -> {
      T obj = e.getValue();
      obj.setNode(new NodeProxy(null, e.getKey()));
      return obj;
    });
  }
  
  /**
   * @return a stream of all unique ids
   */
  public Stream<String> allIds() {
    return ids.keySet().stream();
  }

  /**
   * @return the created node id or null if it could not be created
   */
  public Node create(T obj) {
    Preconditions.checkArgument(obj.getNode() == null, "Object already has a neo4j node");
    return createOrRegister(obj, null);
  }
  
  /**
   * Creates a new neo4j node if none exists yet applying extra properties and labels if given.
   * If a neo4j node already exists only the ID is checked and the object registered in this CRUD store
   * @return the created node id or null if it could not be created (currently only with duplicate IDs).
   */
  Node createOrRegister(T obj, Map<String,Object> extraProps, Label... extraLabels) {
    Preconditions.checkNotNull(obj);
    // create missing ids, sharing the same id between name & taxon
    if (obj.getId() == null) {
      obj.setId(idGen.next());
    }
  
    // assert ID is unique
    if (duplicateID(obj)) {
      return null;
    }
  
  
    // create a new neo4j node if not yet existing
    if (obj.getNode() == null) {
      // update neo4j properties either via batch mode or classic
      Map<String,Object> props = obj.properties();
      if (extraProps != null) {
        if (props.isEmpty()) {
          props = extraProps;
        } else {
          props.putAll(extraProps);
        }
      }
      Label[] labels = extraLabels == null ? obj.getLabels() : ArrayUtils.addAll(obj.getLabels(), extraLabels);
      obj.setNode( createNode.apply(props, labels) );
    }
    
    objects.put(obj.getNode().getId(), obj);
    ids.put(obj.getId(), obj.getNode().getId());
    
    return obj.getNode();
  }
  
  /**
   * @return true if the objects ID already exists
   */
  private boolean duplicateID(T obj) {
    if (ids.containsKey(obj.getId())) {
      LOG.info("Duplicate {}ID {}", objName, obj.getId());
      duplicateCounter++;
      addIssueFunc.accept(obj, Issue.ID_NOT_UNIQUE);
      T obj2 = objByNode(nodeByID(obj.getId()));
      addIssueFunc.accept(obj2, Issue.ID_NOT_UNIQUE);
      return true;
    }
    return false;
  }
  
  /**
   * Updates the object in the  KVP store, keeping ID index as they are.
   * Throws NPE if taxon node did not exist before.
   */
  public void update(T obj) {
    Preconditions.checkNotNull(obj.getNode());
    Map<String,Object> props = obj.properties();
    if (props != null) {
      NeoDbUtils.setProperties(obj.getNode(), props);
    }
    objects.put(obj.getNode().getId(), obj);
  }
  
  /**
   * Removes the neo4j node with all its relations and all entities stored under this node like NeoTaxon.
   */
  T remove(Node n) {
    T obj = objects.remove(n.getId());
    if (obj != null) {
      ids.remove(obj.getId());
    }
    return obj;
  }
  
  public int getDuplicateCounter() {
    return duplicateCounter;
  }
}
