package life.catalogue.matching.util;

import com.esotericsoftware.kryo.Kryo;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import life.catalogue.matching.model.StoredClassification;
import life.catalogue.matching.model.StoredName;
import life.catalogue.matching.model.StoredParsedName;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;

public class IOUtil {

  public static void serialize(StoredClassification sc, OutputStream outputStream)  {
    final Kryo kryo = new Kryo();
    kryo.register(StoredClassification.class);
    kryo.register(StoredName.class);
    kryo.register(ArrayList.class);
    Output output = new Output(outputStream);
    kryo.writeObject(output, sc);
    output.flush();
    output.close();
  }

  public static StoredClassification deserializeStoredClassification(InputStream inputStream)  {
    final Kryo kryo = new Kryo();
    kryo.register(StoredClassification.class);
    kryo.register(StoredName.class);
    kryo.register(ArrayList.class);
    return kryo.readObject(new Input(inputStream), StoredClassification.class);
  }

  public static void serialize(StoredParsedName sc, OutputStream outputStream)  {
    final Kryo kryo = new Kryo();
    kryo.register(StoredParsedName.class);
    kryo.register(StoredParsedName.StoredAuthorship.class);
    kryo.register(ArrayList.class);
    kryo.register(HashSet.class);
    Output output = new Output(outputStream);
    kryo.writeObject(output, sc);
    output.flush();
    output.close();
  }

  public static StoredParsedName deserializeStoredParsedName(InputStream inputStream){
    final Kryo kryo = new Kryo();
    kryo.register(StoredParsedName.class);
    kryo.register(StoredParsedName.StoredAuthorship.class);
    kryo.register(ArrayList.class);
    kryo.register(HashSet.class);
    return kryo.readObject(new Input(inputStream), StoredParsedName.class);
  }
}
