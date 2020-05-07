package life.catalogue.common.func;

/**
 * A functional interface to pass on lambda method references with no arguments and no return type.
 * {@link Runnable} could have been used, but it is strongly overloaded with concurrent threading.
 */
@FunctionalInterface
public interface Procedure {

  void invoke();

}
