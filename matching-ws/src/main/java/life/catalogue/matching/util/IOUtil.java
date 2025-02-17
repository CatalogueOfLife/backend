package life.catalogue.matching.util;

import com.esotericsoftware.kryo.Kryo;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import com.esotericsoftware.kryo.util.Pool;

import life.catalogue.matching.model.StoredClassification;
import life.catalogue.matching.model.StoredName;
import life.catalogue.matching.model.StoredParsedName;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;

@Component
public class IOUtil {

  @Value("${kryo.input.buffer.size: 1024}")
  protected Integer inputBufferSize = 1024; // a typical classification is about 800 bytes
  @Value("${kryo.output.buffer.size: 1024}")
  protected Integer outputBufferSize = 1024;
  @Value("${kryo.output.max.buffer.size: -1}")
  protected Integer maxOutputBufferSize = -1;
  @Value("${kryo.output.pool.size: 16}")
  protected Integer outputPoolSize = 32;
  @Value("${kryo.input.pool.size: 16}")
  protected Integer inputPoolSize = 32;
  @Value("${kryo.pool.size: 16}")
  protected Integer kyroPoolSize = 32;

  public IOUtil() {}

  public IOUtil(Integer inputBufferSize, Integer outputBufferSize, Integer maxOutputBufferSize, Integer outputPoolSize, Integer inputPoolSize) {
    this.inputBufferSize = inputBufferSize;
    this.outputBufferSize = outputBufferSize;
    this.maxOutputBufferSize = maxOutputBufferSize;
    this.outputPoolSize = outputPoolSize;
    this.inputPoolSize = inputPoolSize;
  }

  Pool<Kryo> kryoPool = new Pool<Kryo>(true, false, kyroPoolSize) {
    protected Kryo create () {
      Kryo kryo = new Kryo();
      kryo.register(StoredClassification.class);
      kryo.register(StoredParsedName.class);
      kryo.register(StoredName.class);
      kryo.register(ArrayList.class);
      kryo.register(HashSet.class);
      return kryo;
    }
  };

  Pool<Output> outputPool = new Pool<Output>(true, false, outputPoolSize) {
    protected Output create () {
      return new Output(outputBufferSize, maxOutputBufferSize);
    }
  };

  Pool<Input> inputPool = new Pool<Input>(true, false, inputPoolSize) {
    protected Input create () {
      return new Input(inputBufferSize);
    }
  };

  public void serialize(StoredClassification sc, OutputStream outputStream)  {
    final Kryo kryo = kryoPool.obtain();
    Output output = outputPool.obtain();
    output.setOutputStream(outputStream);
    kryo.writeObject(output, sc);
    output.flush();
    output.close();
    outputPool.free(output);
    kryoPool.free(kryo);
  }

  public StoredClassification deserializeStoredClassification(InputStream inputStream)  {
    final Kryo kryo = kryoPool.obtain();
    Input input = inputPool.obtain();
    input.setInputStream(inputStream);
    StoredClassification sc = kryo.readObject(input, StoredClassification.class);
    kryoPool.free(kryo);
    inputPool.free(input);
    return sc;
  }

  public void serialize(StoredParsedName sc, OutputStream outputStream)  {
    final Kryo kryo = kryoPool.obtain();
    Output output = outputPool.obtain();
    output.setOutputStream(outputStream);
    kryo.writeObject(output, sc);
    output.flush();
    output.close();
    outputPool.free(output);
    kryoPool.free(kryo);
  }

  public StoredParsedName deserializeStoredParsedName(InputStream inputStream){
    final Kryo kryo = kryoPool.obtain();
    Input input = inputPool.obtain();
    input.setInputStream(inputStream);
    StoredParsedName spn = kryo.readObject(new Input(inputStream), StoredParsedName.class);
    kryoPool.free(kryo);
    inputPool.free(input);
    return spn;
  }
}
