package life.catalogue.es.ddl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Forces the Java property with this annotation to be mapped to the specified ES data type. No
 * check is done as to whether that makes sense. This can be used to map int or Integer fields to
 * the KEYWORD datatype, which might perform better under certain circumstances. See comments
 * elsewhere.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface MapToType {

  ESDataType value();

}
