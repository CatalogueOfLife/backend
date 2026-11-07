package life.catalogue.common.kryo.map;

import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Issue;
import life.catalogue.common.kryo.ApiKryoPool;
import life.catalogue.common.kryo.Pools;

import org.gbif.dwc.terms.DwcTerm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mapdb.DataIO;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.serializer.GroupSerializerObjectArray;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The MapDB serializers write the kryo bytes straight from a growable buffer now.
 * They must write exactly the bytes they wrote through a stream before, so existing stores stay readable.
 */
public class MapDbSerializerBytesTest {
  private final Pool<Kryo> pool = new ApiKryoPool(2);
  private final Random rnd = new Random(20261106);

  private byte[] oldBytes(Object value, int bufferSize) throws IOException {
    DataOutput2 out = new DataOutput2();
    Pools.run(pool, kryo -> {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream(bufferSize);
      Output output = new Output(buffer, bufferSize);
      kryo.writeObject(output, value);
      output.close();
      byte[] bytes = buffer.toByteArray();
      DataIO.packInt(out, bytes.length);
      out.write(bytes);
    });
    return out.copyBytes();
  }

  private static <T> byte[] newBytes(GroupSerializerObjectArray<T> ser, T value) throws IOException {
    DataOutput2 out = new DataOutput2();
    ser.serialize(out, value);
    return out.copyBytes();
  }

  private String randomString() {
    final String chars = "abcé \t×中";
    StringBuilder sb = new StringBuilder();
    int len = rnd.nextInt(rnd.nextInt(10) == 0 ? 500 : 20);
    for (int i = 0; i < len; i++) {
      sb.append(chars.charAt(rnd.nextInt(chars.length())));
    }
    return sb.toString();
  }

  @Test
  public void sameBytes() throws IOException {
    // the old stream based output failed for buffers smaller than the largest single write of 9 bytes,
    // production uses 64 to 256
    for (int bufferSize : new int[]{16, 64, 128, 4096}) {
      var verbatimSer = new MapDbObjectSerializer<>(VerbatimRecord.class, pool, bufferSize);
      var setSer = new MapDbStringSetSerializer(pool, bufferSize);
      for (int i = 0; i < 500; i++) {
        VerbatimRecord v = new VerbatimRecord(i, "file" + i, DwcTerm.Taxon);
        for (DwcTerm t : DwcTerm.values()) {
          if (rnd.nextInt(8) == 0) {
            v.put(t, randomString());
          }
        }
        if (rnd.nextBoolean()) {
          v.add(Issue.values()[rnd.nextInt(Issue.values().length)]);
        }
        Set<String> set = new HashSet<>();
        int size = rnd.nextInt(30);
        for (int j = 0; j < size; j++) {
          set.add(randomString());
        }

        byte[] bytes = newBytes(verbatimSer, v);
        assertArrayEquals(oldBytes(v, bufferSize), bytes);
        assertArrayEquals(oldBytes(set, bufferSize), newBytes(setSer, set));
        // and read back
        assertEquals(v, verbatimSer.deserialize(new DataInput2.ByteArray(bytes), bytes.length));
      }
    }
  }
}
