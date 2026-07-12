package life.catalogue.common.kryo;

import com.esotericsoftware.kryo.util.Pool;

/**
 * Helpers to borrow an object from a Kryo {@link Pool}, run some work with it and always return it to
 * the pool, removing the repetitive obtain / try-finally / free boilerplate at call sites.
 *
 * The functional arguments may throw a checked exception, which is propagated to the caller with its
 * original type (the exception type {@code E} is inferred from the lambda). This works for any pooled
 * type, e.g. {@code Pool<Kryo>}, {@code Pool<Output>} or {@code Pool<Input>}.
 */
public final class Pools {
  private Pools() {}

  @FunctionalInterface
  public interface PoolFunction<T, R, E extends Exception> {
    R apply(T obj) throws E;
  }

  @FunctionalInterface
  public interface PoolConsumer<T, E extends Exception> {
    void accept(T obj) throws E;
  }

  /**
   * Borrows an object from the pool, applies {@code fn} and returns its result, always freeing the object afterwards.
   */
  public static <T, R, E extends Exception> R with(Pool<T> pool, PoolFunction<T, R, E> fn) throws E {
    T obj = pool.obtain();
    try {
      return fn.apply(obj);
    } finally {
      pool.free(obj);
    }
  }

  /**
   * Borrows an object from the pool, runs {@code fn} and always frees the object afterwards.
   */
  public static <T, E extends Exception> void run(Pool<T> pool, PoolConsumer<T, E> fn) throws E {
    T obj = pool.obtain();
    try {
      fn.accept(obj);
    } finally {
      pool.free(obj);
    }
  }
}
