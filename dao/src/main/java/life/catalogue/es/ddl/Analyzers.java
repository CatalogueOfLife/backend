package life.catalogue.es.ddl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enumerates the Elasticsearch analyzers used to index a field. Must only be used for string-like fields. By default all string-like fields
 * are indexed as-is (using the no-op "keyword" tokenizer). You don't need to explicitly specify this using this annotation. But as soon as
 * this annotation is present, the field will <i>only</i> be indexed using the provided array of analyzers. Thus, if the
 * {@link Analyzer#KEYWORD KEYWORD} analyzer is not present in the provided array, the field will <i>not</i> be indexed as-is. Also note
 * that annotating a field with an empty array of analyzers is equivalent to annotating it with the @NotIndexed annotation. We still need
 * the @NotIndexed annotation, however, in order to make non-string fields not-indexed.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Analyzers {

  Analyzer[] value();

}
