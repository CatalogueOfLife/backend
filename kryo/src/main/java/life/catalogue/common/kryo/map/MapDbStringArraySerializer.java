package life.catalogue.common.kryo.map;

import life.catalogue.common.kryo.Pools;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;

import org.apache.commons.lang3.NotImplementedException;
import org.mapdb.DataIO;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.serializer.GroupSerializerObjectArray;

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
    Pools.run(pool, kryo -> {
      // a growable buffer without a stream behind it, written out directly. Same bytes, no copies
      Output output = new Output(bufferSize, -1);
      kryo.writeObject(output, value);
      DataIO.packInt(out, output.position());
      out.write(output.getBuffer(), 0, output.position());
    });
  }

  @Override
  public String[] deserialize(DataInput2 in, int available) throws IOException {
    if (available == 0) return null;
    return Pools.with(pool, kryo -> {
      int size = DataIO.unpackInt(in);
      byte[] ret = new byte[size];
      in.readFully(ret);
      return kryo.readObject(new Input(ret), String[].class);
    });
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
