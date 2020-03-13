package life.catalogue.dw.jersey.exception;

import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import io.dropwizard.jersey.errors.ErrorMessage;
import io.dropwizard.jersey.validation.ConstraintMessage;
import io.dropwizard.jersey.validation.JerseyViolationException;
import org.glassfish.jersey.server.model.Invocable;

/**
 * Converts a JerseyViolationException into a http 400 or 422 if its a request based violation.
 * Modified version of {@link }{@link io.dropwizard.jersey.validation.JerseyViolationExceptionMapper}
 * using the generic col error response entity.
 */
@Provider
public class ValidationExceptionMapper implements ExceptionMapper<JerseyViolationException> {
  static final Joiner ERROR_JOINER = Joiner.on(". ").skipNulls();
  
  @Override
  public Response toResponse(JerseyViolationException e) {
    final Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
    final Invocable invocable = e.getInvocable();
    final int status = ConstraintMessage.determineStatus(violations, invocable);
    
    ImmutableList.Builder<String> b = ImmutableList.builder();
    for (ConstraintViolation<?> v : e.getConstraintViolations()) {
      //b.add(String.format("Validation of [%s] failed: %s\n\n", v.getPropertyPath(), v.getMessage()));
      b.add(ConstraintMessage.getMessage(v, invocable));
    }
    return Response
        .status(status)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(new ErrorMessage(status, "Validation error", ERROR_JOINER.join(b.build())))
        .build();
  }
}
