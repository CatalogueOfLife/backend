package life.catalogue.common.fury;

import java.io.IOException;

import org.apache.commons.lang3.NotImplementedException;
import org.mapdb.DataIO;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.serializer.GroupSerializerObjectArray;

/**
 * A mapDB serializer that uses fury under the hood to quickly serialize objects into the mapdb data output/input.
 *
 * @param <T> the class to serialize
 */
public class MapDbSerializer<T> extends GroupSerializerObjectArray<T> {
  private final Class<T> clazz;

  public MapDbSerializer(Class<T> clazz) {
    this.clazz = clazz;
  }
  
  @Override
  public void serialize(DataOutput2 out, T value) throws IOException {
    byte[] bytes = FuryFactory.FURY.serializeJavaObject(value);
    DataIO.packInt(out, bytes.length);
    out.write(bytes);
  }
  
  @Override
  public T deserialize(DataInput2 in, int available) throws IOException {
    if (available == 0) return null;
    int size = DataIO.unpackInt(in);
    byte[] ret = new byte[size];
    in.readFully(ret);
    return FuryFactory.FURY.deserializeJavaObject(ret, clazz);
  }
  
  @Override
  public boolean isTrusted() {
    return true;
  }
  
  @Override
  public int compare(T first, T second) {
    throw new NotImplementedException("compare should not be needed for our mapdb use");
  }
  
}
