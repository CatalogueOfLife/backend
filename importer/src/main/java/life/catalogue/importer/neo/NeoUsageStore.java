package life.catalogue.importer.neo;

import life.catalogue.importer.IdGenerator;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.neo.model.RelType;

import org.mapdb.DB;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;
import com.google.common.base.Preconditions;

public class NeoUsageStore extends NeoCRUDStore<NeoUsage> {

  public NeoUsageStore(DB mapDb, String mapDbName, Pool<Kryo> pool, IdGenerator idGen, NeoDb neoDb) {
    super(mapDb, mapDbName, NeoUsage.class, pool, neoDb, idGen);
  }
  
  @Override
  public Node create(NeoUsage obj, Transaction tx) {
    Preconditions.checkNotNull(obj.nameNode, "Usage requires an existing name node");
    Node nu = super.create(obj, tx);
    if (nu != null) {
      nu.createRelationshipTo(obj.nameNode, RelType.HAS_NAME);
    }
    return nu;
  }

}
