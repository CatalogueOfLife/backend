package life.catalogue.common.collection;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Function;

public class IterUtils {
  
  public static <T> Set<T> setOf(Iterable<T> iterable) {
    return setOf(iterable, x -> x);
  }

  public static <I, V> Set<V> setOf(Iterable<I> iterable, Function<I, V> converter) {
    Set<V> set = new HashSet<>();
    if (iterable != null) {
      Iterator<I> iter = iterable.iterator();
      while (iter.hasNext()) {
        V val = converter.apply(iter.next());
        set.add(val);
      }
    }
    return set;
  }
}
