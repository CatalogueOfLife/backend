package org.col.importer.neo;

import com.esotericsoftware.kryo.pool.KryoPool;
import com.google.common.base.Preconditions;
import org.col.importer.IdGenerator;
import org.col.importer.neo.model.NeoUsage;
import org.col.importer.neo.model.RelType;
import org.mapdb.DB;
import org.neo4j.graphdb.Node;

public class NeoUsageStore extends NeoCRUDStore<NeoUsage> {

  public NeoUsageStore(DB mapDb, String mapDbName, KryoPool pool, IdGenerator idGen, NeoDb neoDb) {
    super(mapDb, mapDbName, NeoUsage.class, pool, neoDb, idGen);
  }
  
  @Override
  public Node create(NeoUsage obj) {
    Preconditions.checkNotNull(obj.nameNode, "Usage requires an existing name node");
    Node nu = super.create(obj);
    if (nu != null) {
      neoDb.createRel(nu, obj.nameNode, RelType.HAS_NAME);
    }
    return nu;
  }

}
