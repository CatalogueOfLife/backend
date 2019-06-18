package org.col.resources;

import java.util.UUID;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import io.dropwizard.auth.Auth;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.*;
import org.col.api.model.coldp.ColdpReference;
import org.col.api.search.ReferenceSearchRequest;
import org.col.dao.ReferenceDao;
import org.col.dw.jersey.MoreMediaTypes;
import org.col.importer.reference.ReferenceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset/{datasetKey}/reference")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class ReferenceResource extends DatasetEntityResource<Reference>  {
  
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
  //@RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public String createColdp(@PathParam("datasetKey") int datasetKey, @Valid ColdpReference obj, @Auth ColUser user) {
    final String id = UUID.randomUUID().toString();
    Reference ref = ReferenceFactory.fromColDP(datasetKey, id, obj.getCitation(), obj.getAuthor(), obj.getYear(), obj.getTitle(),
        obj.getSource(), obj.getDetails(), obj.getDoi(), obj.getLink(), IssueContainer.DevNullLogging.dataset(datasetKey));
    return dao.create(ref, user.getKey());
  }
}
