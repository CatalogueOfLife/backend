package life.catalogue.common.kryo;

import life.catalogue.common.kryo.jdk.JdkImmutableSetSerializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * We only need IntSet so far, so we create an efficient manual class here.
 * For a generic solution to all fastutils classes see Apache Giraph:
 * https://giraph.apache.org/xref/org/apache/giraph/writable/kryo/serializers/FastUtilSerializer.html
 */
public class FastUtilsSerializer extends Serializer<IntSet> {
  private boolean optimizePositive = false;

  public FastUtilsSerializer(boolean optimizePositive) {
    super(false);
    this.optimizePositive = optimizePositive;
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

  /**
   * Creates a new {@link JdkImmutableSetSerializer} and registers its serializer
   * for the several related classes
   *
   * @param kryo the {@link Kryo} instance to set the serializer on
   */
  public static void registerSerializers(Kryo kryo, boolean positiveOnly) {
    final FastUtilsSerializer serializer = new FastUtilsSerializer(positiveOnly);
    kryo.register(IntSet.class, serializer);
    kryo.register(IntOpenHashSet.class, serializer);
  }

  @Override
  public IntSet copy(Kryo kryo, IntSet original) {
    return new IntOpenHashSet(original);
  }
}
