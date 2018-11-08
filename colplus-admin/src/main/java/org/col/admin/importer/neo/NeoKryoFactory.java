package org.col.admin.importer.neo;

import com.esotericsoftware.kryo.Kryo;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.col.admin.importer.neo.model.NeoTaxon;
import org.col.admin.importer.neo.model.RankedName;
import org.col.common.kryo.ApiKryoFactory;
import org.col.common.kryo.NullSerializer;
import org.neo4j.kernel.impl.core.NodeProxy;


/**
 * Creates a kryo factory usable for thread safe kryo pools that can deal with clb api classes.
 * We use Kryo for extremely fast byte serialization of temporary objects.
 * It is used to serialize various information in kvp stores during checklist indexing and nub builds.
 */
public class NeoKryoFactory extends ApiKryoFactory {
  
  @Override
  public Kryo create() {
    Kryo kryo = super.create();
    
    // add normalizer specific models
    kryo.register(NeoTaxon.class);
    kryo.register(RankedName.class);
    
    // fastutil
    kryo.register(IntArrayList.class);
    
    // ignore normalizer node proxies and set them to null upon read:
    kryo.register(NodeProxy.class, new NullSerializer());
    
    return kryo;
  }
}
