package life.catalogue.resources;

import io.dropwizard.auth.Auth;
import io.dropwizard.jersey.jsr310.LocalDateTimeParam;
import life.catalogue.api.model.*;
import life.catalogue.api.model.coldp.ColdpReference;
import life.catalogue.api.search.ReferenceSearchRequest;
import life.catalogue.dao.ReferenceDao;
import life.catalogue.db.mapper.ReferenceMapper;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.MoreMediaTypes;
import life.catalogue.importer.reference.ReferenceFactory;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/dataset/{datasetKey}/reference")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class ReferenceResource extends AbstractDatasetScopedResource<Reference> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ReferenceResource.class);
  private final ReferenceDao dao;
  
  public ReferenceResource(ReferenceDao dao) {
    super(Reference.class, dao);
    this.dao = dao;
  }
  
  @GET
  @Path("search")
  public ResultPage<Reference> search(@PathParam("datasetKey") int datasetKey,
                                      @BeanParam ReferenceSearchRequest req,
                                      @Valid @BeanParam Page page,
                                      @Context SqlSession session) {
    return dao.search(datasetKey, req, page);
  }
  
  /**
   * @return the primary key of the object. Together with the CreatedResponseFilter will return a 201 location
   */
  @POST
  @Consumes(MoreMediaTypes.APP_JSON_COLDP)
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public String createColdp(@PathParam("datasetKey") int datasetKey, @Valid ColdpReference obj, @Auth ColUser user) {
    final String id = UUID.randomUUID().toString();
    Reference ref = ReferenceFactory.fromColDP(datasetKey, id, obj.getCitation(), obj.getAuthor(), obj.getYear(), obj.getTitle(),
        obj.getSource(), obj.getDetails(), obj.getDoi(), obj.getLink(), obj.getRemarks(), IssueContainer.DevNullLogging.dataset(datasetKey));
    return dao.create(ref, user.getKey()).getId();
  }
  
  /**
   * @return the primary key of the object. Together with the CreatedResponseFilter will return a 201 location
   */
  @POST
  @Consumes(MoreMediaTypes.APP_JSON_CSL)
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public String createCsl(@PathParam("datasetKey") int datasetKey, @Valid CslData csl, @Auth ColUser user) {
    if (csl.getId() == null) {
      csl.setId(UUID.randomUUID().toString());
    }
    Reference ref = ReferenceFactory.fromCsl(datasetKey, csl);
    return dao.create(ref, user.getKey()).getId();
  }

  @GET
  @Path("orphans")
  public ResultPage<Reference> listOrphans(@PathParam("datasetKey") int datasetKey,
                                           @QueryParam("before") LocalDateTimeParam before,
                                           @Valid @BeanParam Page page) {
    return dao.listOrphans(datasetKey, before==null ? null : before.get(), page);
  }

  @DELETE
  @Path("orphans")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public int delete(@PathParam("datasetKey") int datasetKey,
                    @QueryParam("before") LocalDateTimeParam before,
                    @Auth ColUser user,
                    @Context SqlSession session) {
    int cnt = session.getMapper(ReferenceMapper.class).deleteOrphans(datasetKey, before==null ? null : before.get());
    session.commit();
    return cnt;
  }
}
