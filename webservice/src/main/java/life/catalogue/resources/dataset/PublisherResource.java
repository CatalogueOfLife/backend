package life.catalogue.resources.dataset;

import io.swagger.v3.oas.annotations.Hidden;

import life.catalogue.api.model.*;
import life.catalogue.api.search.QuerySearchRequest;
import life.catalogue.dao.GbifPublisherDao;
import life.catalogue.dao.PublisherDao;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.filter.ProjectOnly;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.auth.Auth;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Hidden
@Path("/publisher")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class PublisherResource {
  private final GbifPublisherDao pdao;
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(PublisherResource.class);

  public PublisherResource(GbifPublisherDao dao) {
    this.pdao = dao;
  }

  @GET
  public List<Publisher> search(@QueryParam("q") String q) {
    return pdao.search(q);
  }

  @GET
  @Path("{id}")
  public Publisher get(@PathParam("id") UUID id) {
    return pdao.get(id);
  }
}
