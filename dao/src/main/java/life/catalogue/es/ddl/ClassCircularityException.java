package life.catalogue.es.ddl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import life.catalogue.es.ddl.GetterMappingsFactory;

/**
 * Thrown by {@link GetterMappingsFactory#getMapping(Class)} for Java classes that cannot be mapped because of circularity in the object graph.
 */
public class ClassCircularityException extends MappingException {

  private static String MSG_PATTERN = "Illegal recursive nesting of type %s in %s %s";

  public ClassCircularityException(Field field, Class<?> type) {
    super(String.format(MSG_PATTERN, type.getName(), "field", field.getName()));
  }

  public ClassCircularityException(Method method, Class<?> type) {
    super(String.format(MSG_PATTERN, type.getName(), "method", method.getName()));
  }

}
