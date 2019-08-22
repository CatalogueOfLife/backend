package org.col.es.ddl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enumerates the Elasticsearch analyzers used to index a field. Must only be used for string-like fields (getters whose return type maps to
 * the keyword datatype). Note that by default all string-like fields are at least indexed as-is. But as soon as this annotation is present,
 * the field will only be indexed using the provided array of analyzers. Thus, if the {@link Analyzer#KEYWORD KEYWORD} analyzer is not
 * present in the provided array, this field will __not__ be indexed as-is. Also note that annotating a getter with an empty array of analyzers
 * is equivalent to annotating it with the @NotIndexed annotation; it's just not as intuitive to do so.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Analyzers {

  Analyzer[] value();

}
