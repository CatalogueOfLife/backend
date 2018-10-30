package org.col.common.util;

import java.util.Collection;

public class CollectionUtils {

  public static boolean isEmpty(Collection<?> c) {
    return c == null || c.isEmpty();
  }

  public static boolean notEmpty(Collection<?> c) {
    return c != null && !c.isEmpty();
  }

}
