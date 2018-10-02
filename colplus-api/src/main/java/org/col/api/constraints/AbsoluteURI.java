package org.col.api.constraints;

import java.lang.annotation.*;
import javax.validation.Constraint;
import javax.validation.Payload;


import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

@Target({ FIELD, METHOD, PARAMETER, ANNOTATION_TYPE })
@Retention(RUNTIME)
@Constraint(validatedBy = AbsoluteURIValidator.class)
@Documented
public @interface AbsoluteURI {
  String message() default "URI has to be absolute";
  
  Class<?>[] groups() default { };
  
  Class<? extends Payload>[] payload() default { };
  
}