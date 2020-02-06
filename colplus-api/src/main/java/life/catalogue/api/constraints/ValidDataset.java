package life.catalogue.api.constraints;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = DatasetOriginValidator.class)
@Documented
public @interface ValidDataset {
  String message() default "Dataset origin dependencies not valid";
  
  Class<?>[] groups() default {};
  
  Class<? extends Payload>[] payload() default {};
  
}