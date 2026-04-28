package life.catalogue.resources;

import life.catalogue.api.vocab.IdentifierScope;
import life.catalogue.api.vocab.IdentifierScopes;

import java.util.Collection;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Read-only endpoint exposing the curated identifier scope registry.
 */
@Path("/vocab/identifier-scope")
@Produces(MediaType.APPLICATION_JSON)
public class IdentifierScopeResource {

  @GET
  public Collection<IdentifierScope> list() {
    return IdentifierScopes.all();
  }

  @GET
  @Path("{scope}")
  public IdentifierScope get(@PathParam("scope") String scope) {
    IdentifierScope s = IdentifierScopes.byScope(scope);
    if (s == null) {
      throw new NotFoundException("Unknown identifier scope: " + scope);
    }
    return s;
  }
}
