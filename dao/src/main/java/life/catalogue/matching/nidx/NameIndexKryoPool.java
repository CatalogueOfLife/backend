package life.catalogue.matching.nidx;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import life.catalogue.api.model.IndexName;
import life.catalogue.common.kryo.FastUtilsSerializers;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.Rank;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * We use a separate kryo pool for the names index to avoid too often changes to the serialisation format
 * that then requires us to rebuilt the names index mapdb file. Register just the needed classes, no more.
 */
public class NameIndexKryoPool extends Pool<Kryo> {

  public NameIndexKryoPool(int size) {
    super(true, true, size);
  }

  @Override
  public Kryo create() {
    Kryo kryo = new Kryo();
    kryo.setRegistrationRequired(true);
    kryo.register(IndexName.class);
    kryo.register(Authorship.class);
    kryo.register(Rank.class);
    kryo.register(LocalDateTime.class);
    kryo.register(ArrayList.class);
    kryo.register(HashMap.class);
    kryo.register(HashSet.class);
    kryo.register(int[].class);
    kryo.register(ObjectArrayList.class, new FastUtilsSerializers.ArrayListSerializer());
    try {
      kryo.register(Class.forName("java.util.Arrays$ArrayList"));
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
    return kryo;
  }
}
