package life.catalogue.common.func;

import java.util.function.Consumer;

/**
 * @param <T> the type of the first argument to the function
 * @param <E> the type of exception that may be thrown from the operation
 */
@FunctionalInterface
public interface ThrowingConsumer<T, E extends Exception> extends Consumer<T> {

  @Override
  default void accept(final T obj) {
    try {
      acceptThrows(obj);
    } catch (final Exception e) {
      throw wrapException(e);
    }
  }

  default RuntimeException wrapException(Exception e) {
    return new RuntimeException(e);
  }

  void acceptThrows(T obj) throws E;

}
