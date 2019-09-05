package org.col.es.mapping;

import java.net.URI;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.col.es.mapping.ESDataType.BOOLEAN;
import static org.col.es.mapping.ESDataType.BYTE;
import static org.col.es.mapping.ESDataType.DATE;
import static org.col.es.mapping.ESDataType.DOUBLE;
import static org.col.es.mapping.ESDataType.FLOAT;
import static org.col.es.mapping.ESDataType.INTEGER;
import static org.col.es.mapping.ESDataType.KEYWORD;
import static org.col.es.mapping.ESDataType.LONG;
import static org.col.es.mapping.ESDataType.SHORT;

/**
 * Maps Java types to Elasticsearch types and vice versa.
 */
class DataTypeMap {

  static final DataTypeMap INSTANCE = new DataTypeMap();

  private final HashMap<Class<?>, ESDataType> java2es = new HashMap<>();
  private final EnumMap<ESDataType, HashSet<Class<?>>> es2java = new EnumMap<>(ESDataType.class);

  private DataTypeMap() {
    
    /* Stringy types */
    java2es.put(String.class, KEYWORD);
    java2es.put(char.class, KEYWORD);
    java2es.put(Character.class, KEYWORD);
    java2es.put(URI.class, KEYWORD);
    java2es.put(URL.class, KEYWORD);
    java2es.put(UUID.class, KEYWORD);
    
    /* Number types */
    java2es.put(byte.class, BYTE);
    java2es.put(Byte.class, BYTE);
    java2es.put(short.class, SHORT);
    java2es.put(Short.class, BOOLEAN);
    java2es.put(int.class, INTEGER);
    java2es.put(Integer.class, INTEGER);
    java2es.put(long.class, LONG);
    java2es.put(Long.class, LONG);
    java2es.put(float.class, FLOAT);
    java2es.put(Float.class, FLOAT);
    java2es.put(double.class, DOUBLE);
    java2es.put(Double.class, DOUBLE);
    
    /* Boolean types */
    java2es.put(boolean.class, BOOLEAN);
    java2es.put(Boolean.class, BOOLEAN);
    
    /* Date types */
    java2es.put(LocalDateTime.class, DATE);
    java2es.put(LocalDate.class, DATE);

    /* Create reverse map */
    java2es.entrySet().stream().forEach(e -> es2java.computeIfAbsent(e.getValue(), k -> new HashSet<>()).add(e.getKey()));

  }

  /**
   * Whether or not the specified Java type maps to a primitive Elasticsearch type (other than "object" or "nested").
   */
  boolean isESPrimitive(Class<?> javaType) {
    return getESType(javaType) == null;
  }

  /**
   * Returns the Elasticsearch data type corresponding to the specified Java type. If none is found, the superclass of the specified type is
   * checked to see if it corresponds to an Elasticsearch data type, and so on until (but not including) the {@link Object} class.
   */
  ESDataType getESType(Class<?> javaType) {
    ESDataType esDataType = null;
    while (javaType != Object.class) {
      if ((esDataType = java2es.get(javaType)) != null) {
        break;
      }
      javaType = javaType.getSuperclass();
    }
    /*
     * TODO: We could make this more robust if we would also allow and check for interfaces in the datatype map
     */
    return esDataType;
  }

  /**
   * Returns all Java types that map to the specified Elasticsearch data type.
   */
  Set<Class<?>> getJavaTypes(ESDataType esType) {
    return es2java.get(esType);
  }

}
