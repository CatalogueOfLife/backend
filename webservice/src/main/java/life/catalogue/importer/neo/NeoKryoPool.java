package life.catalogue.importer.neo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import life.catalogue.common.kryo.ApiKryoPool;
import life.catalogue.importer.neo.model.NeoName;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.neo.model.NodeMock;
import life.catalogue.importer.neo.model.RankedName;
import life.catalogue.common.kryo.NullSerializer;
import org.neo4j.kernel.impl.core.NodeProxy;


/**
 * Creates a kryo factory usable for thread safe kryo pools that can deal with clb api classes.
 * We use Kryo for extremely fast byte serialization of temporary objects.
 * It is used to serialize various information in kvp stores during checklist indexing and nub builds.
 */
public class NeoKryoPool extends ApiKryoPool {

  public NeoKryoPool(int maximumCapacity) {
    super(maximumCapacity);
  }

  @Override
  public Kryo create() {
    Kryo kryo = super.create();
    
    // add normalizer specific models
    kryo.register(NeoUsage.class);
    kryo.register(NeoName.class);
    kryo.register(RankedName.class);
    
    // fastutil
    kryo.register(IntArrayList.class);
    
    // ignore normalizer node proxies and set them to null upon read:
    kryo.register(NodeProxy.class, new NullSerializer());
    kryo.register(NodeMock.class, new NullSerializer());

    return kryo;
  }
}
