package life.catalogue.resources;

import com.google.common.collect.ImmutableList;

import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Hidden;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.search.QuerySearchRequest;
import life.catalogue.api.search.SectorSearchRequest;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.assembly.SyncManager;
import life.catalogue.dao.*;
import life.catalogue.db.mapper.SectorImportMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.filter.ProjectOnly;
import life.catalogue.matching.decision.RematcherBase;
import life.catalogue.matching.decision.SectorRematchRequest;
import life.catalogue.matching.decision.SectorRematcher;

import org.apache.ibatis.session.SqlSession;

import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

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
