package org.col.jersey.exception;

import com.google.common.collect.ImmutableList;
import org.col.jersey.MoreStatus;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.ext.Provider;

/**
 * Converts a ConstraintViolationException into a http 422 bad request and gives a meaningful messages on the issues.
 */
@Provider
public class ValidationExceptionMapper extends JsonExceptionMapperBase<ConstraintViolationException> {

  public ValidationExceptionMapper() {
    super(MoreStatus.UNPROCESSABLE_ENTITY);
  }

  @Override
  String message(ConstraintViolationException e) {
    ImmutableList.Builder<String> b = ImmutableList.builder();
    for (ConstraintViolation<?> cv : e.getConstraintViolations()) {
      b.add(String.format("Validation of [%s] failed: %s\n\n", cv.getPropertyPath(), cv.getMessage()));
    }
    return b.toString();
  }
}
