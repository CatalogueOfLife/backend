package life.catalogue.common.func;

import java.util.function.Supplier;

@FunctionalInterface
public interface ThrowingSupplier<T, E extends Exception> extends Supplier<T> {

  @Override
  default T get() {
    try {
      return getThrows();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  T getThrows() throws E;

}
