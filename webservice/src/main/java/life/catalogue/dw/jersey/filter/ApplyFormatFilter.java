package life.catalogue.dw.jersey.filter;

import javax.ws.rs.NameBinding;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Binds the {LegacyFormatFilter} to the annotated resource.
 */
@NameBinding
@Retention(RUNTIME)
@Target({TYPE, METHOD})
public @interface ApplyFormatFilter {

}
