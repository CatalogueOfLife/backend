package life.catalogue.common.collection;

import java.util.Iterator;

public class IterUtils {
  
  public static <T> T firstOrNull(Iterable<T> iterable) {
    if (iterable != null) {
      Iterator<T> iter = iterable.iterator();
      if (iter.hasNext()) {
        return iter.next();
      }
    }
    return null;
  }
}
