package life.catalogue.common.kryo.map;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.commons.lang3.NotImplementedException;
import org.mapdb.DataIO;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.serializer.GroupSerializerObjectArray;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;

/**
 * A mapDB serializer that uses kryo under the hood to quickly serialize objects into the mapdb data output/input.
 *
 * @param <T> the class to serialize
 */
public class MapDbObjectSerializer<T> extends GroupSerializerObjectArray<T> {
  private final Pool<Kryo> pool;
  private final int bufferSize;
  private final Class<T> clazz;
  
  public MapDbObjectSerializer(Class<T> clazz, Pool<Kryo> pool, int bufferSize) {
    this.pool = pool;
    this.clazz = clazz;
    this.bufferSize = bufferSize;
  }
  
  @Override
  public void serialize(DataOutput2 out, T value) throws IOException {
    Kryo kryo = null;
    try {
      kryo = pool.obtain();
      ByteArrayOutputStream buffer = new ByteArrayOutputStream(bufferSize);
      Output output = new Output(buffer, bufferSize);
      kryo.writeObject(output, value);
      output.close();
      byte[] bytes = buffer.toByteArray();
      DataIO.packInt(out, bytes.length);
      out.write(bytes);
    } finally {
      if (kryo != null) {
        pool.free(kryo);
      }
    }
  }
  
  @Override
  public T deserialize(DataInput2 in, int available) throws IOException {
    if (available == 0) return null;
    Kryo kryo = null;
    try {
      kryo = pool.obtain();
      int size = DataIO.unpackInt(in);
      byte[] ret = new byte[size];
      in.readFully(ret);
      return kryo.readObject(new Input(ret), clazz);
    } finally {
      if (kryo != null) {
        pool.free(kryo);
      }
    }
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
