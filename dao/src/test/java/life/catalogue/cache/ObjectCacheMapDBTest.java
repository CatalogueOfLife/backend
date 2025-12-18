package life.catalogue.cache;

import com.esotericsoftware.kryo.Kryo;

import life.catalogue.api.event.DoiChange;
import life.catalogue.api.model.HasID;
import life.catalogue.common.io.TempFile;
import life.catalogue.common.kryo.ApiKryoPool;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ObjectCacheMapDBTest {

  @Test
  void crud() throws IOException {
    try (
      TempFile tf = TempFile.file();
    ) {
      ObjectCache<UID> cache = new ObjectCacheMapDB<>(UID.class, tf.file, new XApiKryoPool(), true);
      cache.put(new UID());
      cache.put(new UID());
      cache.put(new UID());
      cache.put(new UID());
      assertEquals(4, cache.size());
      cache.put(new UID());
      cache.put(new UID());
      assertEquals(6, cache.size());
      var uid = new UID();
      cache.put(uid);
      assertEquals(7, cache.size());
      cache.put(new UID(uid.getUUID()));
      assertEquals(7, cache.size());
      cache.remove(uid.getId());
      assertEquals(6, cache.size());

      uid = new UID();
      cache.put(uid);
      assertEquals(7, cache.size());
      cache.close();

      // open again
      cache = new ObjectCacheMapDB<>(UID.class, tf.file, new XApiKryoPool(), true);
      assertEquals(7, cache.size());
      var uid2 = cache.get(uid.getId());
      assertEquals(uid, uid2);

      cache.close();
    }
  }

  static class XApiKryoPool extends ApiKryoPool {
    @Override
    public Kryo create() {
      var k = super.create();
      k.register(UID.class);
      return k;
    }
  }
  static class UID implements HasID<String> {
    final UUID uuid;

    public UID() {
      this(UUID.randomUUID());
    }
    public UID(UUID uuid) {
      this.uuid = uuid;
    }

    @Override
    public String getId() {
      return uuid.toString();
    }

    public UUID getUUID() {
      return uuid;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof UID uid)) return false;

      return Objects.equals(uuid, uid.uuid);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(uuid);
    }
  }
}