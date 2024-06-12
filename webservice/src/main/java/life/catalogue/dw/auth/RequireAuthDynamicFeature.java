package life.catalogue.dw.auth;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Optional;

import javax.annotation.Priority;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.DynamicFeature;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.FeatureContext;

import org.glassfish.jersey.server.internal.LocalizationMessages;
import org.glassfish.jersey.server.model.AnnotatedMethod;

import io.dropwizard.auth.Auth;

/**
 * Makes sure that if a non optional @Auth annotation exists the securit context is provided.
 * Works together with RolesAllowedDynamicFeature which checks for the right role to exist, but simply waves requests with PermitAll annotations.
 */
public class RequireAuthDynamicFeature implements DynamicFeature {

  @Override
  public void configure(ResourceInfo resourceInfo, FeatureContext configuration) {
    // add filter to all @Auth annotated methods that do not yet have
    final AnnotatedMethod am = new AnnotatedMethod(resourceInfo.getResourceMethod());
    // look for @Auth without Optional
    final Annotation[][] parameterAnnotations = am.getParameterAnnotations();
    final Class<?>[] parameterTypes = am.getParameterTypes();
    for (int i = 0; i < parameterAnnotations.length; i++) {
      for (final Annotation annotation : parameterAnnotations[i]) {
        if (annotation instanceof Auth && !parameterTypes[i].equals(Optional.class)) {
          configuration.register(new RequirePrincipalRequestFilter());
        }
      }
    }
  }

  @Priority(Priorities.AUTHORIZATION + 1)
  private static class RequirePrincipalRequestFilter implements ContainerRequestFilter {

    @Override
    public void filter(final ContainerRequestContext req) throws IOException {
      if (req.getSecurityContext().getUserPrincipal() == null) {
        throw new NotAuthorizedException(LocalizationMessages.USER_NOT_AUTHORIZED());
      }
    }
  }
}
