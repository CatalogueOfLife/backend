package life.catalogue.dw.jersey.filter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.ws.rs.NameBinding;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;


/**
 * Specifies that the response varies based on accept headers.
 * The response will therefore be added a Vary: Accept header by binding the VaryResponseFilter
 */
@NameBinding
@Target({TYPE, METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface VaryAccept {}
