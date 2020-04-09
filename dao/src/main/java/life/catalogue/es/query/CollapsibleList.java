package life.catalogue.es.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * An extension of ArrayList solely aimed at reflecting a peculiarity of the Elasticsearch Query DSL, which allows you to write
 * single-element arrays without using array notation (square brackets). Really just syntactic sugar. Array notation
 * is still also allowed.
 */
public class CollapsibleList<E> extends ArrayList<E> {

  public static <T> CollapsibleList<T> of(T one) {
    return new CollapsibleList<>(Arrays.asList(one));
  }

  public static <T> CollapsibleList<T> of(T one, T two) {
    return new CollapsibleList<>(Arrays.asList(one, two));
  }

  @SuppressWarnings("unchecked")
  public static <T> CollapsibleList<T> of(T... items) {
    return new CollapsibleList<>(Arrays.asList(items));
  }

  public CollapsibleList() {
    super();
  }

  public CollapsibleList(int initialCapacity) {
    super(initialCapacity);
  }

  public CollapsibleList(Collection<? extends E> c) {
    super(c);
  }

  @JsonValue
  public Object collapse() {
    switch (size()) {
      case 0:
        return null;
      case 1:
        return get(0);
      default:
        return new ArrayList<>(this);
    }
  }

}
