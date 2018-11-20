package org.col.common.util;

import java.util.Collection;
import java.util.Map;

public class CollectionUtils {

  public static boolean isEmpty(Collection<?> c) {
    return c == null || c.isEmpty();
  }

  public static boolean notEmpty(Collection<?> c) {
    return c != null && !c.isEmpty();
  }

  public static boolean isEmpty(Map<?,?> m) {
    return m == null || m.isEmpty();
  }

  public static boolean notEmpty(Map<?,?> m) {
    return m != null && !m.isEmpty();
  }

  private CollectionUtils() {}

}
