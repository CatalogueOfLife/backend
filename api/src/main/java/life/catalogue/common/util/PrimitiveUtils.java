package life.catalogue.common.util;

public class PrimitiveUtils {

  public static boolean eval(Boolean val) {
    return val != null && val;
  }

  public static int intDefault(Integer val, int defaultValue) {
    return val == null ? defaultValue : val;
  }
}
