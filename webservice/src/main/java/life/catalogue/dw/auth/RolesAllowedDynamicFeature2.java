package life.catalogue.dw.auth;

import java.io.IOException;

import org.glassfish.jersey.server.internal.LocalizationMessages;
import org.glassfish.jersey.server.model.AnnotatedMethod;

import jakarta.annotation.Priority;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.DynamicFeature;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.FeatureContext;

/**
 * Copy of the regular jersey RolesAllowedDynamicFeature, but returns a 401 not authorized
 * when no Authorization was given instead of a (wrong) 403 in the original RolesAllowedRequestFilter
 * - which is private amd cannot be overridden.
 */
public class RolesAllowedDynamicFeature2  implements DynamicFeature {

  @Override
  public void configure(final ResourceInfo resourceInfo, final FeatureContext configuration) {
    final AnnotatedMethod am = new AnnotatedMethod(resourceInfo.getResourceMethod());

    // DenyAll on the method take precedence over RolesAllowed and PermitAll
    if (am.isAnnotationPresent(DenyAll.class)) {
      configuration.register(new RolesAllowedRequestFilter());
      return;
    }

    // RolesAllowed on the method takes precedence over PermitAll
    RolesAllowed ra = am.getAnnotation(RolesAllowed.class);
    if (ra != null) {
      configuration.register(new RolesAllowedRequestFilter(ra.value()));
      return;
    }

    // PermitAll takes precedence over RolesAllowed on the class
    if (am.isAnnotationPresent(PermitAll.class)) {
      // Do nothing.
      return;
    }

    // DenyAll can't be attached to classes

    // RolesAllowed on the class takes precedence over PermitAll
    ra = resourceInfo.getResourceClass().getAnnotation(RolesAllowed.class);
    if (ra != null) {
      configuration.register(new RolesAllowedRequestFilter(ra.value()));
    }
  }

  @Priority(Priorities.AUTHORIZATION) // authorization filter - should go after any authentication filters
  private static class RolesAllowedRequestFilter implements ContainerRequestFilter {

    private final boolean denyAll;
    private final String[] rolesAllowed;

    RolesAllowedRequestFilter() {
      this.denyAll = true;
      this.rolesAllowed = null;
    }

    RolesAllowedRequestFilter(final String[] rolesAllowed) {
      this.denyAll = false;
      this.rolesAllowed = (rolesAllowed != null) ? rolesAllowed : new String[] {};
    }

    @Override
    public void filter(final ContainerRequestContext requestContext) throws IOException {
      if (!denyAll) {
        if (rolesAllowed.length > 0 && !isAuthenticated(requestContext)) {
          throw new NotAuthorizedException(LocalizationMessages.USER_NOT_AUTHORIZED());
        }

        for (final String role : rolesAllowed) {
          if (requestContext.getSecurityContext().isUserInRole(role)) {
            return;
          }
        }
      }

      throw new ForbiddenException(LocalizationMessages.USER_NOT_AUTHORIZED());
    }

    private static boolean isAuthenticated(final ContainerRequestContext requestContext) {
      return requestContext.getSecurityContext().getUserPrincipal() != null;
    }
  }
}
