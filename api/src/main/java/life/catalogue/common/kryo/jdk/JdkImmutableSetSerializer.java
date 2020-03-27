package life.catalogue.common.kryo.jdk;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.Set;

public final class JdkImmutableSetSerializer extends Serializer<Set<Object>> {

  private JdkImmutableSetSerializer() {
    super(false, true);
  }

  @Override
  public void write(Kryo kryo, Output output, Set<Object> object) {
    output.writeInt(object.size(), true);
    for (final Object elm : object) {
      kryo.writeClassAndObject(output, elm);
    }
  }

  @Override
  public Set<Object> read(Kryo kryo, Input input, Class<? extends Set<Object>> type) {
    final int size = input.readInt(true);
    final Object[] list = new Object[size];
    for (int i = 0; i < size; ++i) {
      list[i] = kryo.readClassAndObject(input);
    }
    return Set.of(list);
  }

  /**
   * Creates a new {@link JdkImmutableSetSerializer} and registers its serializer
   * for the several related classes
   *
   * @param kryo the {@link Kryo} instance to set the serializer on
   */
  public static void registerSerializers(Kryo kryo) {
    final JdkImmutableSetSerializer serializer = new JdkImmutableSetSerializer();
    kryo.register(Set.of().getClass(), serializer);
    kryo.register(Set.of(1).getClass(), serializer);
    kryo.register(Set.of(1, 2, 3, 4).getClass(), serializer);
  }
}