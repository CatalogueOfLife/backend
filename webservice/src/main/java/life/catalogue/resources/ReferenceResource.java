package life.catalogue.resources;

import life.catalogue.api.model.*;
import life.catalogue.api.search.ReferenceSearchRequest;
import life.catalogue.dao.ReferenceDao;
import life.catalogue.dw.auth.Roles;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.dw.jersey.filter.VaryAccept;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.auth.Auth;
import io.dropwizard.jersey.jsr310.LocalDateTimeParam;

@Path("/dataset/{key}/reference")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class ReferenceResource extends AbstractDatasetScopedResource<String, Reference, ReferenceSearchRequest> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ReferenceResource.class);
  private final ReferenceDao dao;

  public ReferenceResource(ReferenceDao dao) {
    super(Reference.class, dao);
    this.dao = dao;
  }

  @GET
  @Path("{id}")
  @Override
  @VaryAccept
  @Produces({MediaType.APPLICATION_JSON,
    MoreMediaTypes.APP_JSON_CSL,
    MoreMediaTypes.APP_BIBTEX
  })
  public Reference get(@PathParam("key") int datasetKey, @PathParam("id") String id) {
    return super.get(datasetKey, id);
  }

  @Override
  ResultPage<Reference> searchImpl(int datasetKey, ReferenceSearchRequest request, Page page) {
    return dao.search(datasetKey, request, page);
  }
  
  /**
   * @return the primary key of the object. Together with the CreatedResponseFilter will return a 201 location
   */
  @POST
  @Consumes(MoreMediaTypes.APP_JSON_CSL)
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public String createCsl(@PathParam("key") int datasetKey, @Valid CslData csl, @Auth User user) {
    return dao.create(datasetKey, csl, user.getKey()).getId();
  }

  @GET
  @Path("orphans")
  public ResultPage<Reference> listOrphans(@PathParam("key") int datasetKey,
                                           @QueryParam("before") LocalDateTimeParam before,
                                           @Valid @BeanParam Page page) {
    return dao.listOrphans(datasetKey, before==null ? null : before.get(), page);
  }

  @DELETE
  @Path("orphans")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public int delete(@PathParam("key") int datasetKey,
                    @QueryParam("before") LocalDateTimeParam before,
                    @Auth User user,
                    @Context SqlSession session) {
    return dao.deleteOrphans(datasetKey, before==null ? null : before.get(), user);
  }
}
