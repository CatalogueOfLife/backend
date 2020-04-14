package life.catalogue.dw.auth;

import java.security.Principal;
import java.time.LocalDateTime;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.SecurityContext;

import life.catalogue.api.model.User;
import life.catalogue.api.vocab.Users;

/**
 * Authenticates any request with the local admin test user.
 * WARNING!
 * Never use this filter in production, it is meant for local testing only!!!
 */
@Priority(Priorities.AUTHENTICATION)
public class LocalAuthFilter implements ContainerRequestFilter {
  
  private final User USER = new User();
  
  
  public LocalAuthFilter() {
    USER.setKey(Users.TESTER);
    USER.setUsername("tester");
    USER.setFirstname("Tim");
    USER.setLastname("Tester");
    for (User.Role r : User.Role.values()) {
      USER.addRole(r);
    }
    USER.setEmail("tim.tester@mailinator.com");
    USER.setCreated(LocalDateTime.now());
  }
  
  /**
   * Tries to read the Bearer token from the authorization header if present
   */
  @Override
  public void filter(ContainerRequestContext requestContext) {
    final SecurityContext securityContext = requestContext.getSecurityContext();
    final boolean secure = securityContext != null && securityContext.isSecure();
    
    requestContext.setSecurityContext(new SecurityContext() {
      @Override
      public Principal getUserPrincipal() {
        return USER;
      }
      
      @Override
      public boolean isUserInRole(String role) {
        return USER.hasRole(role, null);
      }
      
      @Override
      public boolean isSecure() {
        return secure;
      }
      
      @Override
      public String getAuthenticationScheme() {
        return "Local";
      }
    });
  }
  
}