package life.catalogue.common.kryo.map;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;

import org.apache.commons.lang3.NotImplementedException;
import org.mapdb.DataIO;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.serializer.GroupSerializerObjectArray;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class MapDbStringArraySerializer extends GroupSerializerObjectArray<String[]> {
  private final Pool<Kryo> pool;
  private final int bufferSize;

  public MapDbStringArraySerializer(Pool<Kryo> pool, int bufferSize) {
    this.pool = pool;
    this.bufferSize = bufferSize;
  }
  
  @Override
  public void serialize(DataOutput2 out, String[] value) throws IOException {
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
  public String[] deserialize(DataInput2 in, int available) throws IOException {
    if (available == 0) return null;
    Kryo kryo = null;
    try {
      kryo = pool.obtain();
      int size = DataIO.unpackInt(in);
      byte[] ret = new byte[size];
      in.readFully(ret);
      return kryo.readObject(new Input(ret), String[].class);
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
  public int compare(String[] first, String[] second) {
    throw new NotImplementedException("compare should not be needed for our mapdb use");
  }
  
}
