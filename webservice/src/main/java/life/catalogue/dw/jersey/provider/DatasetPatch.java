package life.catalogue.dw.jersey.provider;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Specifies that a dataset body parameter is to be parsed as a patch dataset with the DatasetPatchMessageBodyReader
 * which differs between absent and null fields.
 * Needs to be placed on method parameters for reading and the methods for writing a dataset patch response.
 */
@Documented
@Retention(RUNTIME)
@Target({METHOD, PARAMETER})
public @interface DatasetPatch {

}
