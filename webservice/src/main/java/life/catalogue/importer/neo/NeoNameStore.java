package life.catalogue.importer.neo;

import life.catalogue.importer.IdGenerator;
import life.catalogue.importer.neo.model.NeoName;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.mapdb.DB;
import org.mapdb.Serializer;
import org.neo4j.graphdb.Node;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;
import com.google.common.base.Preconditions;

public class NeoNameStore extends NeoCRUDStore<NeoName> {
  
  // scientificName to nodeId
  private final Map<String, long[]> names;
  
  public NeoNameStore(DB mapDb, String mapDbName, Pool<Kryo> pool, IdGenerator idGen, NeoDb neoDb) {
    super(mapDb, mapDbName, NeoName.class, pool, neoDb, idGen);
    names = mapDb.hashMap(mapDbName+"-names")
        .keySerializer(Serializer.STRING)
        .valueSerializer(Serializer.LONG_ARRAY)
        .createOrOpen();
  }
  
  /**
   * @return the matching name nodes with the scientificName in a mutable set
   */
  public HashSet<Node> nodesByName(String scientificName) {
    if (names.containsKey(scientificName)) {
      return Arrays.stream(names.get(scientificName))
          .mapToObj(neoDb::nodeById)
          .collect(Collectors.toCollection(HashSet::new));
    }
    return new HashSet<>();
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
  NeoName remove(Node n) {
    NeoName nn = super.remove(n);
    if (nn != null) {
      remove(nn);
    }
    return nn;
  }
  
  private void remove(NeoName n) {
    if (n.getName().getScientificName() != null) {
      long[] nids = names.get(n.getName().getScientificName());
      if (nids != null) {
        nids = ArrayUtils.removeElement(nids, n.node.getId());
        if (nids.length < 1) {
          names.remove(n.getName().getScientificName());
        } else {
          names.put(n.getName().getScientificName(), nids);
        }
      }
    }
  }
  
  private void add(NeoName n, long nodeId) {
    if (n.getName().getScientificName() != null) {
      long[] nids = names.get(n.getName().getScientificName());
      if (nids == null) {
        nids = new long[]{nodeId};
      } else {
        nids = ArrayUtils.add(nids, nodeId);
      }
      names.put(n.getName().getScientificName(), nids);
    }
  }

  public int size() {
    return names.size();
  }

}
