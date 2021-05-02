package life.catalogue.api.constraints;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.net.URI;

/**
 * Makes sure a URI has at least a scheme and host part defined.
 */
public class AbsoluteURIValidator implements ConstraintValidator<AbsoluteURI, URI> {
  
  @Override
  public boolean isValid(URI uri, ConstraintValidatorContext constraintContext) {
    if (uri == null) {
      return true;
    }

    if (uri.getScheme() == null || uri.getHost() == null) {
      return false;
    }
    return true;
  }
}
