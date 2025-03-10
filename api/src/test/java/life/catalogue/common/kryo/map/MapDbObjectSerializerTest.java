package life.catalogue.common.kryo.map;

import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.common.io.TempFile;
import life.catalogue.common.kryo.ApiKryoPool;

import org.gbif.dwc.terms.DwcTerm;

import java.io.IOException;
import java.util.Map;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;

public class MapDbObjectSerializerTest {
  TempFile tf;

  Pool<Kryo> pool;

  @Before
  public void init() throws IOException {
    tf = TempFile.file();
    pool = new ApiKryoPool(8);
  }
  
  @After
  public void shutdown() {
    tf.close();
  }
  
  @Test
  public void serde() {
  
    DB mapDb = DBMaker
        .fileDB(tf.file)
        .fileMmapEnableIfSupported()
        .make();
    
    Map<Long, VerbatimRecord> verbatim = mapDb.hashMap("verbatim")
        .keySerializer(Serializer.LONG)
        .valueSerializer(new MapDbObjectSerializer(VerbatimRecord.class, pool, 128))
        .create();

    final int repeat = 100;
    StopWatch watch = new StopWatch();
    verbatim.put(0l, gen(0));
    watch.start();
    for (long x=1; x<repeat; x++) {
      verbatim.put(x, gen(x));
    }
    watch.stop();
    System.out.println(watch);

    watch.reset();
    watch.start();
    System.out.println("Reading...");
    for (long x=1; x<repeat; x++) {
      var obj = verbatim.get(x);
    }
    watch.stop();
    System.out.println(watch);

    mapDb.close();
  }
  
  public static VerbatimRecord gen(long x) {
    return new VerbatimRecord(x, "myfile.txt", DwcTerm.Taxon);
  }
}