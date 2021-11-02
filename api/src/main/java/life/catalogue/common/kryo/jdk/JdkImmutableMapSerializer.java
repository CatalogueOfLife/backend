package life.catalogue.common.kryo.jdk;

import java.util.HashMap;
import java.util.Map;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public final class JdkImmutableMapSerializer extends Serializer<Map<Object, Object>> {

  private JdkImmutableMapSerializer() {
    super(false, true);
  }

  @Override
  public void write(Kryo kryo, Output output, Map<Object, Object> object) {
    kryo.writeObject(output, new HashMap<>(object));
  }

  @Override
  public Map<Object, Object> read(Kryo kryo, Input input, Class<? extends Map<Object, Object>> type) {
    final Map map = kryo.readObject(input, HashMap.class);
    return Map.<Object, Object>copyOf(map);
  }

  /**
   * Creates a new {@link JdkImmutableMapSerializer} and registers its serializer
   * for the several related classes
   *
   * @param kryo the {@link Kryo} instance to set the serializer on
   */
  public static void registerSerializers(final Kryo kryo) {
    final JdkImmutableMapSerializer serializer = new JdkImmutableMapSerializer();
    final Object o1 = new Object();
    final Object o2 = new Object();
    kryo.register(Map.of().getClass(), serializer);
    kryo.register(Map.of(o1, o1).getClass(), serializer);
    kryo.register(Map.of(o1, o1, o2, o2).getClass(), serializer);
  }
}