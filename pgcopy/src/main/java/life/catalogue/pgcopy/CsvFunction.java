package life.catalogue.pgcopy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;

public interface CsvFunction extends Function<String[], LinkedHashMap<String, String>> {

  /**
   * Called once to init the function with existing column headers before the function is called for actual row values.
   */
  default void init(List<String> headers) {
    // nothing by default
  }

  /**
   * @return list of columns added by this function
   */
  List<String> columns();
}
