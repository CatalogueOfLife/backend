package org.col.resources;

import java.util.List;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import io.dropwizard.auth.Auth;
import org.apache.ibatis.session.SqlSession;
import org.col.api.exception.NotFoundException;
import org.col.api.model.*;
import org.col.dao.TaxonDao;
import org.col.dw.auth.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset/{datasetKey}/taxon")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class TaxonResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(TaxonResource.class);
  
  @GET
  public ResultPage<Taxon> list(@PathParam("datasetKey") int datasetKey, @QueryParam("root") boolean root,
                                @Valid @BeanParam Page page, @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    return root ? dao.listRoot(datasetKey, page) : dao.list(datasetKey, page);
  }
  
  /**
   * @return the primary key of the object. Together with the CreatedResponseFilter will return a 201 location
   */
  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public String createChild(@PathParam("datasetKey") Integer datasetKey, @Valid Taxon obj, @Auth ColUser user, @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    obj.setDatasetKey(datasetKey);
    dao.create(obj, user);
    session.commit();
    return obj.getId();
  }

  @GET
  @Path("{id}")
  public Taxon get(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    Taxon t = dao.get(datasetKey, id);
    if (t == null) {
      throw NotFoundException.idNotFound(Taxon.class, datasetKey, id);
    }
    return dao.get(datasetKey, id);
  }
  
  
  @PUT
  @Path("{id}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void update(@PathParam("datasetKey") Integer datasetKey, @PathParam("id") String id,
                     @Valid Taxon obj, @Auth ColUser user, @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    obj.setDatasetKey(datasetKey);
    obj.setId(id);
    dao.update(obj, user);
    session.commit();
  }
  
  @DELETE
  @Path("{id}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void delete(@PathParam("datasetKey") Integer datasetKey, @PathParam("id") String id,
                     @Auth ColUser user, @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    dao.delete(new DatasetID(datasetKey, id), user);
    session.commit();
  }
  
  @GET
  @Path("{id}/children")
  public ResultPage<Taxon> children(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Valid @BeanParam Page page,
                                    @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    return dao.getChildren(datasetKey, id, page);
  }
  
  @GET
  @Path("{id}/synonyms")
  public Synonymy synonyms(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    return dao.getSynonymy(datasetKey, id);
  }
  
  @GET
  @Path("{id}/classification")
  public List<Taxon> classification(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    return dao.getClassification(datasetKey, id);
  }
  
  @GET
  @Timed
  @Path("{id}/info")
  public TaxonInfo info(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    TaxonInfo info = dao.getTaxonInfo(datasetKey, id);
    if (info == null) {
      throw NotFoundException.idNotFound(Taxon.class, datasetKey, id);
    }
    return info;
  }
  
}
