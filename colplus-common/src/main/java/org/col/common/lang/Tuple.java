package org.col.common.lang;

/**
 * Immutable Tuple implementation
 */
public final class Tuple<T, U> {

  private final T left;
  private final U right;

  public Tuple(T left, U right) {
    this.left = left;
    this.right = right;
  }

  public T getLeft() {
    return left;
  }

  public U getRight() {
    return right;
  }

}
