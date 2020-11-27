package life.catalogue.common.func;

import java.util.function.BiConsumer;

/**
 * @param <T> the type of the first argument to the function
 * @param <U> the type of the second argument to the function
 * @param <E> the type of exception that may be thrown from the operation
 */
@FunctionalInterface
public interface ThrowingBiConsumer<T, U, E extends Exception> extends BiConsumer<T, U> {

  @Override
  default void accept(T obj, U obj2) {
    try {
      acceptThrows(obj, obj2);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  void acceptThrows(T obj, U obj2) throws E;

}
