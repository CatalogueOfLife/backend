package life.catalogue.resources.dataset;

import life.catalogue.api.model.*;
import life.catalogue.api.search.QuerySearchRequest;
import life.catalogue.dao.PublisherDao;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.filter.ProjectOnly;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.auth.Auth;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/dataset/{key}/sector/publisher")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class PublisherResource extends AbstractDatasetScopedResource<UUID, Publisher, QuerySearchRequest> {
  private final PublisherDao pdao;
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(PublisherResource.class);

  public PublisherResource(PublisherDao dao) {
    super(Publisher.class, dao);
    this.pdao = dao;
  }

  @Override
  ResultPage<Publisher> searchImpl(int datasetKey, QuerySearchRequest req, Page page) {
    return dao.list(datasetKey, page);
  }

  @DELETE
  @ProjectOnly
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void deleteByDataset(@PathParam("key") int datasetKey, @Auth User user) {
    dao.deleteByDataset(datasetKey);
  }

  @GET
  @Path("{id}/metrics")
  public ImportMetrics publisherMetrics(@PathParam("key") int datasetKey, @PathParam("id") UUID id) {
    return pdao.sourceMetrics(datasetKey, id);
  }
}
