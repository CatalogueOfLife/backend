package life.catalogue.resources;

import life.catalogue.dw.auth.Roles;

import java.io.IOException;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import io.swagger.v3.oas.annotations.Hidden;
import life.catalogue.portal.PortalPageRenderer;

@Path("/portal")
@Hidden
@SuppressWarnings("static-method")
public class PortalResource {
  private final PortalPageRenderer renderer;

  public PortalResource(PortalPageRenderer renderer) {
    this.renderer = renderer;
  }

  @PUT
  @Path("source")
  @RolesAllowed({Roles.ADMIN})
  @Consumes({MediaType.TEXT_HTML, MediaType.TEXT_PLAIN})
  public boolean setDatasource(String template) throws IOException {
    return renderer.store(PortalPageRenderer.PortalPage.DATASET, template);
  }

  @GET
  @Path("source/{id}")
  @Produces({MediaType.TEXT_HTML, MediaType.TEXT_PLAIN})
  public String datasource(@PathParam("id") int id, @QueryParam("preview") boolean preview) throws Exception {
    return renderer.renderDatasource(id, preview);
  }

  @PUT
  @Path("taxon")
  @RolesAllowed({Roles.ADMIN})
  @Consumes({MediaType.TEXT_HTML, MediaType.TEXT_PLAIN})
  public boolean setTaxon(String template) throws IOException {
    return renderer.store(PortalPageRenderer.PortalPage.TAXON, template);
  }

  @GET
  @Path("taxon/{id}")
  @Produces({MediaType.TEXT_HTML, MediaType.TEXT_PLAIN})
  public String taxon(@PathParam("id") String id, @QueryParam("preview") boolean preview) throws Exception {
    return renderer.renderTaxon(id, preview);
  }
}
