package life.catalogue.event;

import java.io.*;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;

import life.catalogue.api.event.Event;

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
    Kryo kryo = null;
    Output output = null;
    try {
      kryo = kryoPool.obtain();
      output = outputPool.obtain();
      output.setOutputStream(os);
      kryo.writeClassAndObject(output, event);
      output.flush();
      output.close();

    } finally {
      if (output != null) outputPool.free(output);
      if (kryo != null) kryoPool.free(kryo);
    }
  }

  Object read(InputStream is) {
    Kryo kryo = null;
    Input input = null;
    try {
      kryo = kryoPool.obtain();
      input = inputPool.obtain();
      input.setInputStream(is);
      return kryo.readClassAndObject(input);

    } finally {
      if (input != null) inputPool.free(input);
      if (kryo != null) kryoPool.free(kryo);
    }
  }

}
