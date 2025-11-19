package life.catalogue.importer.store;

import life.catalogue.common.kryo.ApiKryoPool;
import life.catalogue.importer.store.model.NameData;
import life.catalogue.importer.store.model.UsageData;
import life.catalogue.importer.store.model.RankedName;



import com.esotericsoftware.kryo.Kryo;

import it.unimi.dsi.fastutil.ints.IntArrayList;


/**
 * Creates a kryo factory usable for thread safe kryo pools that can deal with clb api classes.
 * We use Kryo for extremely fast byte serialization of temporary objects.
 * It is used to serialize various information in kvp stores during checklist indexing and nub builds.
 */
public class ImportKryoPool extends ApiKryoPool {

  public ImportKryoPool(int maximumCapacity) {
    super(maximumCapacity);
  }

  @Override
  public Kryo create() {
    Kryo kryo = super.create();
    
    // add normalizer specific models
    kryo.register(UsageData.class);
    kryo.register(NameData.class);
    kryo.register(RankedName.class);

    // fastutil
    kryo.register(IntArrayList.class);

    return kryo;
  }
}
