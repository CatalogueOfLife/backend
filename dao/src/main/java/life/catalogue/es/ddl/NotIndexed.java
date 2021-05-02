package life.catalogue.es.ddl;

import life.catalogue.es.query.PrefixQuery;
import life.catalogue.es.query.TermQuery;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the getter decorated with this annotation is not indexed (and hence not searchable). By default all
 * fields are indexed as-is (using the no-op KEYWORD analyzer), so they can be searched using simple {@link TermQuery
 * term queries) and {@link PrefixQuery prefix queries}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface NotIndexed {

}
