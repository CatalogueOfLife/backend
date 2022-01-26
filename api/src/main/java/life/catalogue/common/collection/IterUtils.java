package life.catalogue.common.collection;

import java.util.*;
import java.util.function.Function;

import com.google.common.base.Preconditions;

public class IterUtils {
  
  public static <T> T firstOrNull(Iterable<T> iterable) {
    if (iterable != null) {
      Iterator<T> iter = iterable.iterator();
      if (iter.hasNext()) {
        return iter.next();
      }
    }
    return null;
  }

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

  public static <T> Iterable<List<T>> group(Iterable<T> iterable, Comparator<T> comparator) {
    Preconditions.checkNotNull(iterable);
    return () -> new GroupIterator<>(iterable, comparator);
  }

  static class GroupIterator<T> implements Iterator<List<T>> {
    private final Comparator<T> comp;
    private final Iterator<T> iter;
    private final List<T> group = new ArrayList<>();
    private T next;

    public GroupIterator(Iterable<T> iterable, Comparator<T> comperator) {
      this.iter = iterable.iterator();
      this.comp = comperator;
      if (iter.hasNext() && next == null) {
        next = iter.next();
      }
    }

    @Override
    public boolean hasNext() {
      return next != null || iter.hasNext();
    }

    @Override
    public List<T> next() {
      if (next == null) return null;

      T first = next;
      next = null;
      group.clear();
      group.add(first);
      while(iter.hasNext()) {
        T obj = iter.next();
        if (comp.compare(first, obj)==0) {
          group.add(obj);
        } else {
          next = obj;
          break;
        }
      }
      return group;
    }
  }
}
