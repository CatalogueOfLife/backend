package life.catalogue.matching.util;

import com.esotericsoftware.kryo.Kryo;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import com.esotericsoftware.kryo.util.Pool;

import life.catalogue.matching.model.StoredClassification;
import life.catalogue.matching.model.StoredName;
import life.catalogue.matching.model.StoredParsedName;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;

public class IOUtil {

  private static final IOUtil INSTANCE = new IOUtil();

  private IOUtil() {
  }

  public static IOUtil getInstance() {
    return INSTANCE;
  }

  Pool<Kryo> kryoPool = new Pool<Kryo>(true, false, 8) {
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

  public void serialize(StoredClassification sc, OutputStream outputStream)  {
    final Kryo kryo = kryoPool.obtain();
    Output output = new Output(outputStream);
    kryo.writeObject(output, sc);
    output.flush();
    output.close();
    kryoPool.free(kryo);
  }

  public StoredClassification deserializeStoredClassification(InputStream inputStream)  {
    final Kryo kryo = kryoPool.obtain();
    StoredClassification sc = kryo.readObject(new Input(inputStream), StoredClassification.class);
    kryoPool.free(kryo);
    return sc;
  }

  public void serialize(StoredParsedName sc, OutputStream outputStream)  {
    final Kryo kryo = kryoPool.obtain();
    Output output = new Output(outputStream);
    kryo.writeObject(output, sc);
    output.flush();
    output.close();
    kryoPool.free(kryo);
  }

  public StoredParsedName deserializeStoredParsedName(InputStream inputStream){
    final Kryo kryo = kryoPool.obtain();
    StoredParsedName spn = kryo.readObject(new Input(inputStream), StoredParsedName.class);
    kryoPool.free(kryo);
    return spn;
  }
}
