package life.catalogue.api.constraints;

import java.net.URI;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * Makes sure a URI has at least a scheme and host part defined.
 */
public class AbsoluteURIValidator implements ConstraintValidator<AbsoluteURI, URI> {
  
  @Override
  public boolean isValid(URI uri, ConstraintValidatorContext constraintContext) {
    if (uri == null) {
      return true;
    }
    return isAbsolut(uri);
  }

  public static boolean isAbsolut(URI uri) {
    return uri.getScheme() != null && uri.getHost() != null;
  }
}
