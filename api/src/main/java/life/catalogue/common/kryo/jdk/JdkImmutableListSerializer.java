package life.catalogue.common.kryo.jdk;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.List;

public final class JdkImmutableListSerializer extends Serializer<List<Object>> {

  private JdkImmutableListSerializer() {
    super(false, true);
  }

  @Override
  public void write(Kryo kryo, Output output, List<Object> object) {
    output.writeInt(object.size(), true);
    for (final Object elm : object) {
      kryo.writeClassAndObject(output, elm);
    }
  }

  @Override
  public List<Object> read(Kryo kryo, Input input, Class<List<Object>> type) {
    final int size = input.readInt(true);
    final Object[] list = new Object[size];
    for (int i = 0; i < size; ++i) {
      list[i] = kryo.readClassAndObject(input);
    }
    return List.of(list);
  }

  /**
   * Creates a new {@link JdkImmutableListSerializer} and registers its serializer
   * for the several related classes
   *
   * @param kryo the {@link Kryo} instance to set the serializer on
   */
  public static void registerSerializers(final Kryo kryo) {
    final JdkImmutableListSerializer serializer = new JdkImmutableListSerializer();
    kryo.register(List.of().getClass(), serializer);
    kryo.register(List.of(1).getClass(), serializer);
    kryo.register(List.of(1, 2, 3, 4).getClass(), serializer);
    kryo.register(List.of(1, 2, 3, 4).subList(0, 2).getClass(), serializer);
    kryo.register(List.of(1, 2, 3, 4).iterator().getClass(), serializer);
  }
}