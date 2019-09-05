package org.col.es.mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enumerates the Elasticsearch analyzers used to index a field. Must only be used for string-like fields (that is: on
 * getters whose return type maps to the keyword datatype). Note that by default all string-like fields are at least
 * indexed as-is (using the no-op "keyword" tokenizer). But as soon as this annotation is present, the field will only
 * be indexed using the provided array of analyzers. Thus, if the {@link Analyzer#KEYWORD KEYWORD} analyzer is not
 * present in the provided array, the field will <i>not</i> be indexed as-is. Also note that annotating a getter with an
 * empty array of analyzers is equivalent to annotating it with the @NotIndexed annotation. However, it's not as
 * intuitive, so we maintain a separate @NotIndexed annotation for that particular purpose.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Analyzers {

  Analyzer[] value();

}
