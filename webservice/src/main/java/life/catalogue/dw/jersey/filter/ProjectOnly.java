package life.catalogue.dw.jersey.filter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.ws.rs.NameBinding;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

/**
 * Resource method annotation to restrict access to projects only.
 * Annotated methods will be forbidden if the targeted dataset is not in a project.
 */
@NameBinding
@Target({TYPE, METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ProjectOnly {
}
