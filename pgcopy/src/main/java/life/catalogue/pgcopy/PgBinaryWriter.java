package life.catalogue.pgcopy;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public class PgBinaryWriter implements AutoCloseable {
  final static Charset CHARSET = StandardCharsets.UTF_8;
  final static String HEADER = "PGCOPY\n\377\r\n\0";
  private final transient DataOutputStream buffer;
  public PgBinaryWriter(OutputStream out) throws IOException {
    this(out, 65536);
  }

  public PgBinaryWriter(OutputStream out, int bufferSize) throws IOException {
    buffer = new DataOutputStream(new BufferedOutputStream(out, bufferSize));
    writeHeader();
  }

  public void startRow(int columns) throws IOException {
    buffer.writeShort(columns);
  }
  
  @Override
  public void close() throws IOException {
    buffer.writeShort(-1);

    buffer.flush();
    buffer.close();
  }

  private void writeHeader() throws IOException {
    // 11 bytes required header
    buffer.writeBytes(HEADER);
    // 32 bit integer indicating no OID
    buffer.writeInt(0);
    // 32 bit header extension area length
    buffer.writeInt(0);
  }

  public DataOutputStream getBuffer() {
    return buffer;
  }

  public void flush() throws IOException {
    buffer.flush();
  }


  public void writePBoolean(boolean value) throws IOException {
    writePBoolean(value, buffer);
  }
  private void writePBoolean(boolean value, DataOutputStream out) throws IOException {
    out.writeInt(1);
    if (value) {
      out.writeByte(1);
    } else {
      out.writeByte(0);
    }
  }

  public void writePByte(int value) throws IOException {
    writePByte(value, buffer);
  }
  private void writePByte(int value, DataOutputStream out) throws IOException {
    out.writeInt(1);
    out.writeByte(value);
  }

  /**
   * Writes primitive short to the output stream
   *
   * @param value value to write
   *
   */
  public void writePShort(int value) throws IOException {
    writePShort(value, buffer);
  }
  private void writePShort(int value, DataOutputStream out) throws IOException {
    out.writeInt(2);
    out.writeShort(value);
  }

  /**
   * Writes primitive integer to the output stream
   *
   * @param value value to write
   *
   */
  public void writePInt(int value) throws IOException {
    writePInt(value, buffer);
  }
  private void writePInt(int value, DataOutputStream out) throws IOException {
    out.writeInt(4);
    out.writeInt(value);
  }
  /**
   * Writes primitive long to the output stream
   *
   * @param value value to write
   *
   */
  public void writePLong(long value) throws IOException {
    writePLong(value, buffer);
  }
  private void writePLong(long value, DataOutputStream out) throws IOException {
    out.writeInt(8);
    out.writeLong(value);
  }
  /**
   * Writes primitive float to the output stream
   *
   * @param value value to write
   *
   */
  public void writePFloat(float value) throws IOException {
    writePFloat(value, buffer);
  }
  private void writePFloat(float value, DataOutputStream out) throws IOException {
    out.writeInt(4);
    out.writeFloat(value);
  }
  /**
   * Writes primitive double to the output stream
   *
   * @param value value to write
   *
   */
  public void writePDouble(double value) throws IOException {
    writePDouble(value, buffer);
  }
  private void writePDouble(double value, DataOutputStream out) throws IOException {
    out.writeInt(8);
    out.writeDouble(value);
  }

  public void writePByteArray(byte[] value) throws IOException {
    writePByteArray(value, buffer);
  }
  private void writePByteArray(byte[] value, DataOutputStream out) throws IOException {
    out.writeInt(value.length);
    out.write(value, 0, value.length);
  }

  /**
   * Writes a Null Value.
   */
  public void writeNull() throws IOException {
    writeNull(buffer);
  }
  private void writeNull(DataOutputStream out) throws IOException {
    out.writeInt(-1);
  }

  private <T> void writeObject(T value, DataOutputStream out, ThrowingBiConsumer<T, DataOutputStream> nonNullWriter) {
    try {
      if (value == null) {
        writeNull(out);
      } else {
        nonNullWriter.acceptThrows(value, out);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void writeInteger(Integer value) {
    writeObject(value, buffer, this::writePInt);
  }
  private void writeInteger(Integer value, DataOutputStream out) {
    writeObject(value, out, this::writePInt);
  }

  public void writeLong(Long value) {
    writeObject(value, buffer, this::writePLong);
  }

  public void writeDouble(Double value) {
    writeObject(value, buffer, this::writePDouble);
  }

  public void writeBoolean(Boolean value) {
    writeObject(value, buffer, this::writePBoolean);
  }

  public void writeString(String value) throws IOException {
    writeString(value, buffer);
  }

  private void writeString(String value, DataOutputStream out) {
    try {
      if (value == null) {
        writeNull(out);
      } else {
        writePByteArray(value.getBytes(CHARSET), out);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void writeStringArray(Collection<String> value) throws IOException {
    writeArray(ObjectIdentifier.Text, value, this::writeString);
  }
  public void writeIntegerArray(Collection<Integer> value) throws IOException {
    writeArray(ObjectIdentifier.Int4, value, this::writeInteger);
  }
  public void writeUUID(UUID value) throws IOException {
    if (value == null) {
      writeNull();
    } else {
      buffer.writeInt(16);

      ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
      bb.putLong(value.getMostSignificantBits());
      bb.putLong(value.getLeastSignificantBits());

      buffer.writeInt(bb.getInt(0));
      buffer.writeShort(bb.getShort(4));
      buffer.writeShort(bb.getShort(6));

      buffer.write(Arrays.copyOfRange(bb.array(), 8, 16));
    }
  }

  public void writeHstore(Map<String, String> value) throws IOException {
    if (value == null) {
      writeNull();
    } else {
      // Write into a Temporary ByteArrayOutputStream:
      ByteArrayOutputStream byteArrayOutput = new ByteArrayOutputStream();

      // And wrap it in a DataOutputStream:
      DataOutputStream hstoreOutput = new DataOutputStream(byteArrayOutput);

      // First the Amount of Values to write:
      hstoreOutput.writeInt(value.size());

      // Now Iterate over the Array and write each value:
      for (Map.Entry<String, String> entry : value.entrySet()) {
        // Write the Key:
        writeString(entry.getKey(), hstoreOutput);
        // The Value can be null, use a different method:
        writeString(entry.getValue(), hstoreOutput);
      }

      // Now write the entire ByteArray to the COPY Buffer:
      buffer.writeInt(byteArrayOutput.size());
      buffer.write(byteArrayOutput.toByteArray());
    }
  }

  public <T extends Enum<?>> void writeEnum(T value) {
    writeObject(value, buffer, (val,out) -> writePByteArray(val.name().getBytes(CHARSET), out));
  }
  public <T extends Enum<?>> void writeEnumArray(Collection<T> value) throws IOException {
    writeArray(ObjectIdentifier.Text, value, (val,out) -> writePByteArray(val.name().getBytes(CHARSET), out));
  }

  private <T> void writeArray(int oid, Collection<T> value, ThrowingBiConsumer<T, DataOutputStream> typeWriter) throws IOException {
    if (value == null) {
      writeNull();
      return;
    }

    ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream();
    DataOutputStream arrayOut = new DataOutputStream(byteArrayOut);

    arrayOut.writeInt(1); // Dimensions, use 1 for one-dimensional arrays at the moment
    arrayOut.writeInt(1); // The Array can contain Null Values
    arrayOut.writeInt(oid); // Write the Values using the OID
    arrayOut.writeInt(value.size()); // Write the number of elements
    arrayOut.writeInt(1); // Ignore Lower Bound. Use PG Default for now

    // Now write the actual Collection elements using the inner handler:
    for (T element : value) {
      typeWriter.accept(element, arrayOut);
    }

    buffer.writeInt(byteArrayOut.size());
    buffer.write(byteArrayOut.toByteArray());
  }

  @FunctionalInterface
  private interface ThrowingBiConsumer<T, U> extends BiConsumer<T, U> {

    @Override
    default void accept(T obj, U obj2) {
      try {
        acceptThrows(obj, obj2);
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }

    void acceptThrows(T obj, U obj2) throws IOException;
  }
}
