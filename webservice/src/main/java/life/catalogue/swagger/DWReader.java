package life.catalogue.swagger;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;

import jakarta.ws.rs.Consumes;

import com.fasterxml.jackson.annotation.JsonView;

import io.dropwizard.auth.Auth;
import io.swagger.v3.jaxrs2.Reader;
import io.swagger.v3.jaxrs2.ResolvedParameter;
import io.swagger.v3.oas.models.Operation;

/**
 * Modified reader that skip all parameters marked with the {@link Auth} interface which is provided by Dropwizard
 * and should not be a request body.
 */
public class DWReader extends Reader {

  @Override
  protected ResolvedParameter getParameters(Type type, List<Annotation> annotations, Operation operation, Consumes classConsumes, Consumes methodConsumes, JsonView jsonViewAnnotation) {
    ResolvedParameter rp = super.getParameters(type, annotations, operation, classConsumes, methodConsumes, jsonViewAnnotation);
    for (Annotation ano : annotations) {
      if (ano instanceof Auth) {
        rp.parameters.clear();
        rp.formParameters.clear();
        rp.requestBody = null;
      }
    }
    return rp;
  }
}
