package org.col.admin.importer.neo;

import com.esotericsoftware.kryo.pool.KryoPool;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.ArrayUtils;
import org.col.admin.importer.IdGenerator;
import org.col.admin.importer.neo.model.NeoName;
import org.mapdb.DB;
import org.mapdb.Serializer;
import org.neo4j.graphdb.Node;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NeoNameStore extends NeoCRUDStore<NeoName> {
  
  // scientificName to nodeId
  private final Map<String, long[]> names;
  
  public NeoNameStore(DB mapDb, String mapDbName, KryoPool pool, IdGenerator idGen, NeoDb neoDb) {
    super(mapDb, mapDbName, NeoName.class, pool, neoDb, idGen);
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
          .mapToObj(neoDb::nodeById)
          .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }
  
  @Override
  public Node create(NeoName obj) {
    Node n = super.create(obj);
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
  public NeoName remove(Node n) {
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
