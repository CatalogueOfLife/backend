package org.col.api.constraints;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import org.apache.commons.lang3.StringUtils;

/**
 * Makes sure a string is not null and not just whitespace.
 */
public class NotBlankValidator implements ConstraintValidator<NotBlank, String> {
  
  @Override
  public boolean isValid(String x, ConstraintValidatorContext constraintContext) {
    if (StringUtils.isBlank(x)) {
      return false;
    }
    return true;
  }
}
