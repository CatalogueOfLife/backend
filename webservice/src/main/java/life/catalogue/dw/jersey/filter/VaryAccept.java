package life.catalogue.dw.jersey.filter;

import javax.ws.rs.NameBinding;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


/**
 * Specifies that the response varies based on accept headers.
 * The response will therefore be added a Vary: Accept header by binding the VaryResponseFilter
 */

@NameBinding
@Retention(RetentionPolicy.RUNTIME)
public @interface VaryAccept {}
