package life.catalogue.dw.jersey;

import life.catalogue.api.vocab.DatasetOrigin;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Specifies that a managed dataset is required for the method to be executed,
 * otherwise a BadRequest should be returned.
 *
 * It can be specified on a class or on methods. Specifying it on the class
 * means that it applies to all methods of the class. If specified at the
 * method level, it only affects that method.
 */
@Documented
@Retention(RUNTIME)
@Target({TYPE, METHOD})
public @interface OriginRequired {

  /**
   * Optional name of the path parameter that matches the parameter to retrieve the datasetKey
   */
  String value() default "datasetKey";

  DatasetOrigin origin() default DatasetOrigin.MANAGED;
}
