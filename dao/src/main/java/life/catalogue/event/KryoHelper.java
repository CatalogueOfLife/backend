package life.catalogue.event;

import life.catalogue.api.event.Event;
import life.catalogue.common.kryo.Pools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;

public class KryoHelper {

  private final Pool<Kryo> kryoPool;
  private final Pool<Output> outputPool;
  private final Pool<Input> inputPool;

  public KryoHelper(BrokerConfig cfg) {
    kryoPool = new EventKryoPool(cfg.poolSize);
    outputPool = new Pool<Output>(true, false, cfg.poolSize) {
      protected Output create () {
        return new Output(cfg.bufferSize);
      }
    };
    inputPool = new Pool<Input>(true, false, cfg.poolSize) {
      protected Input create() {
        return new Input(cfg.bufferSize);

      }
    };
  }


  <T extends Event> void write(T event, OutputStream os) throws IOException {
    Pools.run(kryoPool, kryo ->
      Pools.run(outputPool, output -> {
        output.setOutputStream(os);
        kryo.writeClassAndObject(output, event);
        output.flush();
        output.close();
      })
    );
  }

  Object read(InputStream is) {
    return Pools.with(kryoPool, kryo ->
      Pools.with(inputPool, input -> {
        input.setInputStream(is);
        return kryo.readClassAndObject(input);
      })
    );
  }

}
