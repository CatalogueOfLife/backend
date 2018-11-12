package org.col.api.constraints;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = AbsoluteURIValidator.class)
@Documented
public @interface AbsoluteURI {
  String message() default "URI has to be absolute";
  
  Class<?>[] groups() default {};
  
  Class<? extends Payload>[] payload() default {};
  
}