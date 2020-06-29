package life.catalogue.es.ddl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Fields/getters with this annotation will not be included in the document type mapping. Note that this may force you to make sure that the
 * field/getter always is/returns null when indexing documents. Otherwise (given that we use strict typing) Elasticsearch would complain
 * about the presence of an undeclared field. You can also use the @JsonIgnore annotation for the same purpose, but that may be too drastic.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface NotMapped {

}
