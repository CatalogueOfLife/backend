package life.catalogue.postgres;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * A simple integer based serial/sequence generator to be used with the PgCopyUtils function parameter.
 */
public class IntSerial implements Function<String[], String> {
  private final AtomicInteger value;

  public IntSerial() {
    value = new AtomicInteger(1);
  }

  public IntSerial(int start) {
    value = new AtomicInteger(start);
  }

  @Override
  public String apply(String[] strings) {
    return String.valueOf(value.getAndIncrement());
  }
}
