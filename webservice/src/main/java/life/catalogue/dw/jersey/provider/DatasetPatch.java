package life.catalogue.dw.jersey.provider;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Specifies that a dataset body parameter is to be parsed as a patch dataset with the DatasetPatchMessageBodyReader
 * which differs between absent and null fields.
 */
@Documented
@Retention(RUNTIME)
@Target({METHOD})
public @interface DatasetPatch {

}
