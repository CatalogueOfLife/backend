package life.catalogue.common.kryo;

import java.io.IOException;

import org.junit.Test;

import com.esotericsoftware.kryo.util.Pool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PoolsTest {

  static class ObjectPool extends Pool<Object> {
    ObjectPool() {
      super(true, false, 8);
    }
    @Override
    protected Object create() {
      return new Object();
    }
  }

  @Test
  public void freesOnReturn() {
    var pool = new ObjectPool();
    String r = Pools.with(pool, o -> "ok");
    assertEquals("ok", r);
    assertEquals(1, pool.getFree());
  }

  @Test
  public void freesOnException() {
    var pool = new ObjectPool();
    try {
      Pools.run(pool, o -> {
        throw new IOException("boom");
      });
      fail("expected IOException");
    } catch (IOException e) {
      // expected, and the borrowed object must still be returned
    }
    assertEquals(1, pool.getFree());
  }
}
