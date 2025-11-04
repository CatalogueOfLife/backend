package life.catalogue.importer.neo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;
import com.google.common.base.Preconditions;

import com.univocity.parsers.csv.CsvWriter;
import life.catalogue.importer.IdGenerator;
import life.catalogue.importer.neo.model.NeoName;

import life.catalogue.importer.neo.model.NeoProperties;
import life.catalogue.importer.neo.model.PropLabel;
import org.apache.commons.lang3.ArrayUtils;
import org.mapdb.DB;
import org.mapdb.Serializer;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

public class NeoNameStore extends NeoCRUDStore<NeoName> {
  
  // scientificName to nodeId
  private final Map<String, int[]> names;
  private final CsvWriter csvNodeWriter;

  public NeoNameStore(DB mapDb, String mapDbName, Pool<Kryo> pool, IdGenerator idGen, NeoDb neoDb) throws IOException {
    super(mapDb, mapDbName, NeoName.class, pool, neoDb, idGen);
    names = mapDb.hashMap(mapDbName+"-names")
        .keySerializer(Serializer.STRING)
        .valueSerializer(Serializer.INT_ARRAY)
        .createOrOpen();
    csvNodeWriter  = neoDb.newCsvWriter(nodeFileName(), NeoProperties.ID, NeoProperties.SCIENTIFIC_NAME, NeoProperties.AUTHORSHIP, NeoProperties.RANK);
  }
  
  /**
   * @return the matching name nodes with the scientificName in a mutable set
   */
  public HashSet<Node> nodesByName(String scientificName, Transaction tx) {
    if (names.containsKey(scientificName)) {
      return Arrays.stream(names.get(scientificName))
          .mapToObj(id -> neoDb.nodeById(id, tx))
          .collect(Collectors.toCollection(HashSet::new));
    }
    return new HashSet<>();
  }
  
  @Override
  public Node create(NeoName obj, Transaction tx) {
    Node n = super.create(obj, tx);
    if (n != null) {
      int id = NeoDbUtils.id(n);
      add(obj, id);
    }
    return n;
  }
  
  @Override
  public void update(NeoName obj, Transaction tx) {
    Preconditions.checkNotNull(obj.node);
    remove(obj);
    super.update(obj, tx);
    add(obj, NeoDbUtils.id(obj.node));
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
      int[] nids = names.get(n.getName().getScientificName());
      if (nids != null) {
        nids = ArrayUtils.removeElement(nids, NeoDbUtils.id(n.node));
        if (nids.length < 1) {
          names.remove(n.getName().getScientificName());
        } else {
          names.put(n.getName().getScientificName(), nids);
        }
      }
    }
  }
  
  private void add(NeoName n, int nodeId) {
    if (n.getName().getScientificName() != null) {
      int[] nids = names.get(n.getName().getScientificName());
      if (nids == null) {
        nids = new int[]{nodeId};
      } else {
        nids = ArrayUtils.add(nids, nodeId);
      }
      names.put(n.getName().getScientificName(), nids);
    }
  }

  public int size() {
    return names.size();
  }

  public String nodeFileName() {
    return csvFileName("");
  }

  @Override
  void writeCsvNode(NeoName obj) {
    final PropLabel props = obj.propLabel();
    csvNodeWriter.writeRow(props);
  }

  @Override
  protected void closeWriters() {
    super.closeWriters();
    if (csvNodeWriter != null) {
      csvNodeWriter.close();
    }
  }
}
