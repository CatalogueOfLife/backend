package life.catalogue.resources.dataset;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.dao.PublisherDao;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import static life.catalogue.api.model.User.userkey;

/**
 * The GBIF publishers ChecklistBank knows, i.e. the organisations of its datasets and of the users' organisation roles.
 */
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

  /**
   * Lists the publishers with the number of their datasets the user can read, optionally matching a full text query.
   * Sorted by relevance with a query, else by the number of datasets.
   */
  @GET
  public ResultPage<PublisherInfo> list(@QueryParam("q") String q,
                                        @QueryParam("sortBy") PublisherInfo.SortBy sortBy,
                                        @Valid @BeanParam Page page,
                                        @Auth Optional<User> user) {
    return pdao.list(q, sortBy, userkey(user), page);
  }

  @GET
  @Path("{id}")
  public Publisher get(@PathParam("id") UUID id) {
    Publisher p = pdao.get(id);
    if (p == null) {
      throw NotFoundException.notFound(Publisher.class, id);
    }
    return p;
  }

  /**
   * Counts the datasets of the publisher the user can read by origin, type and license.
   */
  @GET
  @Path("{id}/metrics")
  public PublisherMetrics metrics(@PathParam("id") UUID id, @Auth Optional<User> user) {
    return pdao.metrics(id, userkey(user));
  }
}
