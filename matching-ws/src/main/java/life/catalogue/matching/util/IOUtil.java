package life.catalogue.matching.util;

import life.catalogue.matching.model.StoredClassification;
import life.catalogue.matching.model.StoredName;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;

import org.apache.lucene.document.StoredField;
import org.apache.lucene.util.BytesRef;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
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
  protected Integer kryoPoolSize = 32;

  public IOUtil() {}

  public IOUtil(Integer inputBufferSize, Integer outputBufferSize, Integer maxOutputBufferSize, Integer outputPoolSize, Integer inputPoolSize) {
    this.inputBufferSize = inputBufferSize;
    this.outputBufferSize = outputBufferSize;
    this.maxOutputBufferSize = maxOutputBufferSize;
    this.outputPoolSize = outputPoolSize;
    this.inputPoolSize = inputPoolSize;
  }

  private final Pool<Kryo> kryoPool = new Pool<Kryo>(true, false, kryoPoolSize) {
    protected Kryo create () {
      Kryo kryo = new Kryo();
      kryo.register(StoredClassification.class);
      kryo.register(StoredName.class);
      kryo.register(ArrayList.class);
      kryo.register(HashSet.class);
      return kryo;
    }
  };

  private final Pool<Output> outputPool = new Pool<Output>(true, false, outputPoolSize) {
    protected Output create () {
      return new Output(outputBufferSize, maxOutputBufferSize);
    }
  };

  private final Pool<Input> inputPool = new Pool<Input>(true, false, inputPoolSize) {
    protected Input create () {
      return new Input(inputBufferSize);
    }
  };

  public <T> Optional<T> deserialiseField(org.apache.lucene.document.Document doc, String fieldName, Class<T> clazz)  {
    BytesRef bytesRef = doc.getBinaryValue(fieldName);
    if (bytesRef != null) {
      byte[] kryoData = bytesRef.bytes;
      if (kryoData != null) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(kryoData,
          bytesRef.offset, bytesRef.length)) {
          T deserializedObject = deserializeObject(inputStream, clazz);
          return Optional.of(deserializedObject);
        } catch (IOException e) {
          log.error("Error deserializing field " + fieldName, e);
        }
      }
    }
    return Optional.empty();
  }

  public <T> void serialiseField(org.apache.lucene.document.Document doc, String fieldName, T theObject) throws IOException {

    // Serialize the User object to a byte array
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    serializeObject(theObject, outputStream);
    outputStream.close();

    byte[] kryoBytes = outputStream.toByteArray();
    doc.add(new StoredField(
      fieldName,
      kryoBytes));
  }

  private <T> T deserializeObject(InputStream inputStream, Class<T> clazz) {
    final Kryo kryo = kryoPool.obtain();
    Input input = inputPool.obtain();
    input.setInputStream(inputStream);
    T sc = kryo.readObject(input, clazz);
    kryoPool.free(kryo);
    inputPool.free(input);
    return sc;
  }

  private <T> void serializeObject(T object, OutputStream outputStream) {
    final Kryo kryo = kryoPool.obtain();
    Output output = outputPool.obtain();
    output.setOutputStream(outputStream);
    kryo.writeObject(output, object);
    output.flush();
    output.close();
    outputPool.free(output);
    kryoPool.free(kryo);
  }
}
