package org.col.es.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collection;

/**
 * Indicates that the field decorated with this annotation is not mapped as a nested object,
 * i&#46;e&#46; it Elasticsearch datatype will be "object" rather than "nested". By default arrays
 * and {@link Collection} objects are always mapped as nested objects. This annotation signifies
 * that the annotated field is an exception to this rule. Fields that are NOT arrays or collections
 * are never mapped to the "nested" datatype, so they don't need the <code>&#64;NotNested</code>
 * annotation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface NotNested {

}
