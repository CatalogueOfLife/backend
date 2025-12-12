package life.catalogue.resources.dataset;

import life.catalogue.api.model.Publisher;
import life.catalogue.dao.PublisherDao;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Hidden
@Path("/publisher")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class PublisherResource {
  private final PublisherDao pdao;
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(PublisherResource.class);

  public PublisherResource(PublisherDao dao) {
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
