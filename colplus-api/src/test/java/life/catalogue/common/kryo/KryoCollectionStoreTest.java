package life.catalogue.common.kryo;

import com.esotericsoftware.kryo.pool.KryoPool;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.Reference;
import life.catalogue.common.io.TempFile;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 *
 */
public class KryoCollectionStoreTest {

  @Test
  public void storeReferences() throws Exception {

    KryoPool pool = new KryoPool.Builder(new ApiKryoFactory())
        .softReferences()
        .build();

    try (TempFile tf = new TempFile("kryo-", ".bin");
         KryoCollectionStore<Page> store = new KryoCollectionStore(Page.class, tf.file, pool)) {

      for (int i = 0; i < 100; i++) {
        Page r = buildPage();
        r.setOffset(i);
        store.add(r);
      }

      int counter = 0;
      for (Page r : store) {
        System.out.println(r);
        assertNotNull(r);
        assertEquals(counter, r.getOffset());
        counter++;
      }

      assertEquals(counter, 100);
    }
  }

  private Page buildPage() {
    return new Page(10, 12);
  }

  private Reference buildRef() {
    Reference r = new Reference();
    r.getCsl().setTitle("Harry Belafonte");
    r.setYear(1989);
    r.setId("randomLatinString(12)");
    return r;
  }

}