package org.col.es.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enumerates the Elasticsearch analyzers used to index a field. Should only be used to decorate
 * String fields and enum fields (which map to Elasticsearch's string type). By default String
 * fields will be indexed using Elasticsearch's default analyzer and the
 * {@link Analyzer#CASE_INSENSITIVE} analyzer while enum fields will only be indexed using the
 * {@link Analyzer#CASE_INSENSITIVE} analyzer (full-text search would ordinarily not make sense for
 * them). If that's OK for a particular field, you don't need to decorate it with this annotation.
 * If you want a field to be indexed using an ngram analyzer you need to use this annotation and
 * list all analyzers that you want to apply to the field. If you don't want any analyzers to apply
 * (meaning you can only search for exact matches with the search string), you must decorate the
 * field with an empty Analyzer array.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Analyzers {
  
  Analyzer[] value();
  
}
