package life.catalogue.matching;

import life.catalogue.api.model.SimpleNameCached;

import java.io.IOException;

import org.apache.commons.lang3.NotImplementedException;
import jakarta.validation.constraints.NotNull;
import org.mapdb.DataIO;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.serializer.GroupSerializerObjectArray;

/**
 * A mapDB serializer that uses fury under the hood to quickly serialize objects into the mapdb data output/input.
 */
public class MapDbStorageSerializer extends GroupSerializerObjectArray<SimpleNameCached> {

  @Override
  public void serialize(@NotNull DataOutput2 out, @NotNull SimpleNameCached value) throws IOException {
    byte[] bytes = UsageMatcherFactory.FURY.serializeJavaObject(value);
    DataIO.packInt(out, bytes.length);
    out.write(bytes);
  }
  
  @Override
  public SimpleNameCached deserialize(@NotNull DataInput2 in, int available) throws IOException {
    if (available == 0) return null;
    int size = DataIO.unpackInt(in);
    byte[] ret = new byte[size];
    in.readFully(ret);
    return UsageMatcherFactory.FURY.deserializeJavaObject(ret, SimpleNameCached.class);
  }
  
  @Override
  public boolean isTrusted() {
    return true;
  }
  
  @Override
  public int compare(SimpleNameCached first, SimpleNameCached second) {
    throw new NotImplementedException("compare should not be needed for our mapdb use");
  }
  
}
