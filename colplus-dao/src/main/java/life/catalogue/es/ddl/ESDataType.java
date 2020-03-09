package life.catalogue.es.ddl;

import java.util.Arrays;
import java.util.HashMap;

/**
 * Symbolic constants for the data types a field can have in an Elasticsearch document type mapping.
 */
public enum ESDataType {

  KEYWORD, TEXT, INTEGER, BOOLEAN, DATE, BYTE, SHORT, LONG, FLOAT, DOUBLE, GEO_POINT, GEO_SHAPE, BINARY, OBJECT, NESTED;

  /*
   * Map data type names to enum constants.
   */
  private static final HashMap<String, ESDataType> reverse;

  static {
    reverse = new HashMap<>(values().length, 1F);
    Arrays.stream(values()).forEach(d -> reverse.put(d.esName, d));
  }

  public static ESDataType parse(String name) {
    return reverse.get(name);
  }

  private final String esName;

  private ESDataType() {
    this.esName = name().toLowerCase();
  }

  private ESDataType(String esName) {
    this.esName = esName;
  }

  @Override
  public String toString() {
    return esName;
  }
}
