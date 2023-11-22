package life.catalogue.pgcopy;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class PgBinaryReader implements AutoCloseable {
  private PgBinaryStream in;
  public PgBinaryReader(InputStream in) throws IOException {
    this.in = new PgBinaryStream(in);
  }

  public boolean startRow() throws IOException {
    return in.readShort() > -1;
  }
  public int readPInt() throws IOException {
    assertInt(4);
    return in.readInt();
  }

  public Integer readInteger() throws IOException {
    if (nullOrInt(4)) return null;
    return in.readInt();
  }

  public Double readDouble() throws IOException {
    if (nullOrInt(8)) return null;
    return in.readDouble();
  }

  public Boolean readBoolean() throws IOException {
    if (nullOrInt(1)) return null;
    return in.readByte() == 1;
  }
  public String readString() throws IOException {
    return readString(in);
  }
  public List<String> readStringArray() throws IOException {
    return readArray(ObjectIdentifier.Text, this::readString);
  }
  private String readString(DataInputStream stream) throws IOException {
    int length = stream.readInt();
    if (length == -1) return null;
    byte[] bytes = stream.readNBytes(length);
    return new String(bytes, PgBinaryWriter.CHARSET);
  }

  private <T> List<T> readArray(int oid, ThrowingReader<T> typeReader) throws IOException {
    int byteLength = in.readInt(); // total bytes of entire array content
    if (byteLength == -1) return null;
    int dim = in.readInt(); // Dimensions, use 1 for one-dimensional arrays at the moment
    int nullAllowed = in.readInt(); // 1= the Array can contain Null Values
    assertInt(oid); // OID
    if (dim == 0 && nullAllowed == 0) return null;

    int size = in.readInt(); // number of elements
    final List<T> array = new ArrayList<>(size);
    int lowerBound = in.readInt();
    for (int i = 0; i<size; i++) {
      array.add(typeReader.apply(in));
    }
    return array;
  }

  public <T extends Enum<?>> T readEnum(Class<T> clazz) throws IOException {
    String x = readString();
    if (x == null) return null;
    return lookupEnum(x, clazz);
  }

  private static <T extends Enum<?>> T lookupEnum(String name, Class<T> vocab) {
    T[] values = vocab.getEnumConstants();
    if (values != null) {
      for (T val : values) {
        if (name.equals(val.name())) {
          return val;
        }
      }
    }
    return null;
  }

  /**
   * @return true if null, false if good to read
   */
  private boolean nullOrInt(int expected) throws IOException {
    int x = in.readInt();
    if (x == -1) return true;
    if (x != expected) throw new IllegalStateException("Unexpected integer "+x+" found. Expected "+expected);
    return false;
  }
  private void assertInt(int expected) throws IOException {
    int x = in.readInt();
    if (x != expected) throw new IllegalStateException("Unexpected integer "+x+" found. Expected "+expected);
  }
  @Override
  public void close() throws Exception {
    in.close();
  }

  @FunctionalInterface
  private interface ThrowingReader<T> extends Function<DataInputStream, T> {

    @Override
    default T apply(DataInputStream out) {
      try {
        return applyThrows(out);
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }

    T applyThrows(DataInputStream out) throws IOException;

  }
}
