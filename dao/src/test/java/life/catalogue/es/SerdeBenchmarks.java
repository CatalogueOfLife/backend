package life.catalogue.es;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.common.fury.FuryFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.Ignore;
import org.junit.Test;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

@Ignore
public class SerdeBenchmarks {
  final private static EsKryoPool kryoPool = new EsKryoPool(64);
  final private static ObjectWriter jackWriter = ApiModule.MAPPER.writerFor(NameUsageWrapper.class);
  final private static ObjectReader jackReader = ApiModule.MAPPER.readerFor(NameUsageWrapper.class);


  @Test
  @Ignore
  public void performance() throws Exception {
    final int repeat = 1_000_000;
    NameUsageWrapper obj = TestEntityGenerator.newNameUsageTaxonWrapperComplete();

    performanceFury(obj, repeat);
    performanceKryo(obj, repeat);
    // needs jackson subtype annotations on NameUsage and subclasses first which we dont want in prod !!!
    //performanceJackson(obj, repeat);
  }

  void performanceFury(final NameUsageWrapper obj, final int repeat) throws Exception {
    // warm up once
    byte[] bytes = FuryFactory.FURY.serializeJavaObject(obj);
    NameUsageWrapper obj2 = FuryFactory.FURY.deserializeJavaObject(bytes, NameUsageWrapper.class);

    StopWatch watch = StopWatch.createStarted();
    for (int i = 0; i < repeat; i++) {
      // serialize
      bytes = FuryFactory.FURY.serializeJavaObject(obj);
      // deserialize
      obj2 = FuryFactory.FURY.deserializeJavaObject(bytes, NameUsageWrapper.class);
    }
    watch.stop();
    printTime("FURY", watch, repeat);
  }

  void serdeKryo(final NameUsageWrapper obj) {
    Kryo kryo = kryoPool.obtain();;
    try {
      // serialize
      ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);
      Output out= new Output(buffer, 1024);
      kryo.writeObject(out, obj);
      out.close();
      byte[] bytes = buffer.toByteArray();
      // deserialize
      var obj2 = kryo.readObject(new Input(bytes), NameUsageWrapper.class);

    } finally {
      if (kryo != null) {
        kryoPool.free(kryo);
      }
    }
  }

  void performanceKryo(final NameUsageWrapper obj, final int repeat) throws Exception {
    // warm up once
    serdeKryo(obj);

    StopWatch watch = StopWatch.createStarted();
    for (int i = 0; i < repeat; i++) {
      serdeKryo(obj);
    }
    watch.stop();
    printTime("KRYO", watch, repeat);
  }

  void performanceJackson(final NameUsageWrapper obj, final int repeat) throws Exception {
    // warm up once
    serdeJackson(obj);

    StopWatch watch = StopWatch.createStarted();
    for (int i = 0; i < repeat; i++) {
      serdeJackson(obj);
    }
    watch.stop();
    printTime("JACKSON", watch, repeat);
  }

  void serdeJackson(final NameUsageWrapper obj) throws IOException {
    // serialize
    StringWriter writer = new StringWriter();
    jackWriter.writeValue(writer, obj);
    // deserialize
    NameUsageWrapper obj2 = jackReader.readValue(writer.toString());
  }

  void printTime(String type, StopWatch watch, int repeat) {
    System.out.println(type + ":");
    System.out.println("  " + watch);
    var performance = watch.getTime() * 1_000 / repeat;
    System.out.println("  " + performance + " ms/ 1k serde");
  }

}