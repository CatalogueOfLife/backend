package life.catalogue.common.util;

public class ReflectUtils {

  private ReflectUtils(){};

  public static <T> Class<T> forceInit(Class<T> klass) {
    try {
      Class.forName(klass.getName(), true, klass.getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new AssertionError(e);  // Can't happen
    }
    return klass;
  }
}
