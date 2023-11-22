package life.catalogue.postgres;

import de.bytefish.pgbulkinsert.pgsql.PgBinaryWriter;
import de.bytefish.pgbulkinsert.pgsql.constants.DataType;
import de.bytefish.pgbulkinsert.pgsql.constants.ObjectIdentifier;
import de.bytefish.pgbulkinsert.pgsql.handlers.BaseValueHandler;
import de.bytefish.pgbulkinsert.pgsql.handlers.CollectionValueHandler;
import de.bytefish.pgbulkinsert.pgsql.handlers.IValueHandler;
import de.bytefish.pgbulkinsert.pgsql.handlers.ValueHandlerProvider;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public class PgBinaryXWriter extends PgBinaryWriter {
  final ValueHandlerProvider provider = new ValueHandlerProvider();
  ;
  final EnumValueHandler enumValueHandler = new EnumValueHandler();

  public PgBinaryXWriter(OutputStream out) {
    super(out);
  }

  public PgBinaryXWriter(OutputStream out, int bufferSize) {
    super(out, bufferSize);
  }

  public void writeInteger(Integer value) {
    writeValue(DataType.Int4, value);
  }

  public void writeLong(Long value) {
    writeValue(DataType.Int8, value);
  }

  public void writeDouble(Double value) {
    writeValue(DataType.DoublePrecision, value);
  }

  public void writeDate(LocalDate value) {
    writeValue(DataType.Date, value);
  }

  public void writeBoolean(Boolean value) {
    writeValue(DataType.Boolean, value);
  }

  public void writeText(String value) {
    writeValue(DataType.Text, value);
  }

  public void writeUUID(UUID value) {
    writeValue(DataType.Uuid, value);
  }

  public void writeHstore(Map<String, String> value) {
    writeValue(DataType.Hstore, value);
  }

  public void writeByteArray(byte[] value) {
    writeValue(DataType.Bytea, value);
  }

  public void writeTextArray(Collection<String> value) {
    setCollection(DataType.Text, value);
  }

  public <T extends Enum> void writeEnum(T value) {
    write(enumValueHandler, value);
  }

  public <TElementType, TCollectionType extends Collection<TElementType>> void setCollection(DataType type, TCollectionType value) {
    final CollectionValueHandler<TElementType, TCollectionType> handler = new CollectionValueHandler<>(ObjectIdentifier.mapFrom(type), provider.resolve(type));
    write(handler, value);
  }

  public <TTargetType> void writeValue(DataType type, TTargetType value) {
    final IValueHandler<TTargetType> handler = provider.resolve(type);
    write(handler, value);
  }

  static class EnumValueHandler extends BaseValueHandler<Enum> {

    @Override
    protected void internalHandle(DataOutputStream buffer, final Enum value) throws Exception {
      byte[] utf8Bytes = de.bytefish.pgbulkinsert.util.StringUtils.getUtf8Bytes(value.name());

      buffer.writeInt(utf8Bytes.length);
      buffer.write(utf8Bytes);
    }

    @Override
    public int getLength(Enum value) {
      byte[] utf8Bytes = de.bytefish.pgbulkinsert.util.StringUtils.getUtf8Bytes(value.name());
      return utf8Bytes.length;
    }
  }
}
