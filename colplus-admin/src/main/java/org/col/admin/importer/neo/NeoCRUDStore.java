package org.col.admin.importer.neo;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.stream.Stream;

import com.esotericsoftware.kryo.pool.KryoPool;
import com.google.common.base.Preconditions;
import org.col.admin.importer.IdGenerator;
import org.col.admin.importer.neo.model.NeoNode;
import org.col.admin.importer.neo.model.NodeMock;
import org.col.admin.importer.neo.model.PropLabel;
import org.col.api.model.ID;
import org.col.api.model.VerbatimEntity;
import org.col.api.vocab.Issue;
import org.col.common.mapdb.MapDbObjectSerializer;
import org.mapdb.DB;
import org.mapdb.Serializer;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeoCRUDStore<T extends ID & VerbatimEntity & NeoNode> {
  private static final Logger LOG = LoggerFactory.getLogger(NeoCRUDStore.class);
  // nodeId -> obj
  private final Map<Long, T> objects;
  // ID -> nodeId
  private final Map<String, Long> ids;
  private int duplicateCounter = 0;
  private final IdGenerator idGen;
  private final String objName;
  private final BiConsumer<VerbatimEntity, Issue> addIssueFunc;
  private final Function<PropLabel, Node> createNode;
  private final BiConsumer<Long, PropLabel> createWithNode;
  protected final LongFunction<Node> nodeById;
  
  NeoCRUDStore( DB mapDb, String mapDbName, Class<T> clazz, KryoPool pool,
                IdGenerator idGen,
                BiConsumer<VerbatimEntity, Issue> addIssueFunc,
                Function<PropLabel, Node> createNode,
                BiConsumer<Long, PropLabel> createWithNode,
                LongFunction<Node> nodeById) {
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
    this.createWithNode = createWithNode;
    this.nodeById = nodeById;
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
    return nodeId == null ? null : nodeById.apply(nodeId);
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
      obj.setNode(new NodeMock(e.getKey()));
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
   * Creates a new usage, but attached to an already existing neo4j node
   */
  public Node createWithNode(T obj) {
    Preconditions.checkNotNull(obj.getNode(), "createWithNode requires a neo4j node");
    return createOrRegister(obj, null);
  }
  
  /**
   * Creates a new neo4j node if none exists yet applying extra propLabel and labels if given.
   * If a neo4j node already exists only the ID is checked and the object registered in this CRUD store
   * @return the created node id or null if it could not be created (currently only with duplicate IDs).
   */
  Node createOrRegister(T obj, Map<String,Object> extraProps, Label... extraLabels) {
    Preconditions.checkNotNull(obj);
    // create missing ids, sharing the same id between name & taxon
    if (obj.getId() == null) {
      obj.setId(idGen.next());
      LOG.debug("Generate new {} ID: {}", obj.getClass().getSimpleName(), obj.getId());
    }
  
    // assert ID is unique
    if (duplicateID(obj)) {
      return null;
    }
    
    final PropLabel props = obj.propLabel();
    if (extraProps != null) {
      props.putAll(extraProps);
    }
    if (extraLabels != null) {
      props.addLabels(extraLabels);
    }

    // create a new neo4j node if not yet existing
    if (obj.getNode() == null) {
      // update neo4j propLabel either via batch mode or classic
      obj.setNode( createNode.apply(props) );
    
    } else {
      // update existing node labels and props with object data
      createWithNode.accept(obj.getNode().getId(), props);
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
    if (!(obj.getNode() instanceof NodeMock)) {
      Map<String,Object> props = obj.propLabel();
      if (props != null && !props.isEmpty()) {
        NeoDbUtils.setProperties(obj.getNode(), props);
      }
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
