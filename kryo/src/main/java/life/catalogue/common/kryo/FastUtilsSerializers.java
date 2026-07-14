package life.catalogue.common.kryo;

import life.catalogue.common.kryo.jdk.JdkImmutableSetSerializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * We only need IntSet so far, so we create an efficient manual class here.
 * For a generic solution to all fastutils classes see Apache Giraph:
 * https://giraph.apache.org/xref/org/apache/giraph/writable/kryo/serializers/FastUtilSerializer.html
 */
public class FastUtilsSerializers {

  /**
   * Creates a new {@link JdkImmutableSetSerializer} and registers its serializer
   * for the several related classes
   *
   * @param kryo the {@link Kryo} instance to set the serializer on
   */
  public static void registerSerializers(Kryo kryo) {
    final IntSetSerializer serializer = new IntSetSerializer();
    kryo.register(IntSet.class, serializer);
    kryo.register(IntOpenHashSet.class, serializer);
    kryo.register(ObjectArrayList.class, new ArrayListSerializer());
  }

  public static class IntSetSerializer extends Serializer<IntSet> {
    private boolean optimizePositive = true;

    public IntSetSerializer() {
      super(false);
    }

    @Override
    public void write(Kryo kryo, Output output, IntSet set) {
      output.writeInt(set.size(), true);
      for (int x : set) {
        output.writeInt(x, optimizePositive);
      }
    }

    @Override
    public IntSet read(final Kryo kryo, final Input input, final Class<? extends IntSet> type) {
      final int size = input.readInt(true);
      final IntSet result = new IntOpenHashSet(size);
      for (int i = 0; i < size; i++) {
        result.add(input.readInt(optimizePositive));
      }
      return result;
    }

    @Override
    public IntSet copy(Kryo kryo, IntSet original) {
      return new IntOpenHashSet(original);
    }
  }

  /**
   * Heavily inspired by kryos ArraysAsListSerializer
   */
  public static class ArrayListSerializer extends Serializer<ObjectArrayList<String>> {

    public ArrayListSerializer() {
      super(false);
    }

    @Override
    public void write(Kryo kryo, Output output, ObjectArrayList<String> list) {
      output.writeInt(list.size(), true);
      for( final String item : list) {
        output.writeString(item);
      }
    }

    @Override
    public ObjectArrayList<String> read(final Kryo kryo, final Input input, final Class<? extends ObjectArrayList<String>> type) {
      final int length = input.readInt(true);
      final ObjectArrayList<String> items = new ObjectArrayList<>(length);
      for( int i = 0; i < length; i++ ) {
        items.add(i, input.readString());
      }
      return items;
    }

    @Override
    public ObjectArrayList<String> copy(Kryo kryo, ObjectArrayList<String> original) {
      return new ObjectArrayList<>(original);
    }

  }
}
