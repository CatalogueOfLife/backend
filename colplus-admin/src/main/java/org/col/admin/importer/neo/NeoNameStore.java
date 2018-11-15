package org.col.admin.importer.neo;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.LongFunction;
import java.util.stream.Collectors;

import com.esotericsoftware.kryo.pool.KryoPool;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.ArrayUtils;
import org.col.admin.importer.IdGenerator;
import org.col.admin.importer.neo.model.NeoName;
import org.col.api.model.VerbatimEntity;
import org.col.api.vocab.Issue;
import org.mapdb.DB;
import org.mapdb.Serializer;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;

public class NeoNameStore extends NeoCRUDStore<NeoName> {
  
  // scientificName to nodeId
  private final Map<String, long[]> names;
  
  public NeoNameStore(DB mapDb, String mapDbName, Class<NeoName> clazz, KryoPool pool, IdGenerator idGen,
                      BiConsumer<VerbatimEntity, Issue> addIssueFunc,
                      BiFunction<Map<String, Object>, Label[], Node> createNode,
                      LongFunction<Node> nodeById) {
    super(mapDb, mapDbName, clazz, pool, idGen, addIssueFunc, createNode, nodeById);
    names = mapDb.hashMap(mapDbName+"-names")
        .keySerializer(Serializer.STRING)
        .valueSerializer(Serializer.LONG_ARRAY)
        .createOrOpen();
  }
  
  /**
   * @return the matching nodes with the scientificName
   */
  public List<Node> nodesByName(String scientificName) {
    if (names.containsKey(scientificName)) {
      return Arrays.stream(names.get(scientificName))
          .mapToObj(nodeById)
          .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }
  
  @Override
  Node createOrRegister(NeoName obj, Map<String, Object> extraProps, Label... extraLabels) {
    Node n = super.createOrRegister(obj, extraProps, extraLabels);
    if (n != null) {
      add(obj, n.getId());
    }
    return n;
  }
  
  @Override
  public void update(NeoName obj) {
    Preconditions.checkNotNull(obj.node);
    remove(obj);
    super.update(obj);
    add(obj, obj.node.getId());
  }
  
  @Override
  NeoName remove(Node n) {
    NeoName nn = super.remove(n);
    remove(nn);
    return nn;
  }
  
  private void remove(NeoName n) {
    if (n.name.getScientificName() != null) {
      long[] nids = names.get(n.name.getScientificName());
      if (nids != null) {
        nids = ArrayUtils.removeElement(nids, n.node.getId());
        if (nids.length < 1) {
          names.remove(n.name.getScientificName());
        } else {
          names.put(n.name.getScientificName(), nids);
        }
      }
    }
  }
  
  private void add(NeoName n, long nodeId) {
    if (n.name.getScientificName() != null) {
      long[] nids = names.get(n.name.getScientificName());
      if (nids == null) {
        nids = new long[]{nodeId};
      } else {
        nids = ArrayUtils.add(nids, nodeId);
      }
      names.put(n.name.getScientificName(), nids);
    }
  }
}
