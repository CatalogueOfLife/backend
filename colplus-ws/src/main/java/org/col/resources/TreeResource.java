package org.col.resources;

import io.dropwizard.auth.Auth;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.*;
import org.col.api.vocab.Origin;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.ReferenceMapper;
import org.col.db.mapper.TaxonMapper;
import org.col.db.mapper.TreeMapper;
import org.col.dw.auth.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

@Path("/dataset/{datasetKey}/tree")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class TreeResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(TreeResource.class);


  /**
   * @return the primary key of the object. Together with the CreatedResponseFilter will return a 201 location
   */
  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public String create(@PathParam("datasetKey") Integer datasetKey, @Valid TreeNode obj,
                       @Auth ColUser user, @Context SqlSession session) {

    NameMapper nm = session.getMapper(NameMapper.class);
    Name n = newKey(nm.getByTaxon(obj.getDatasetKey(), obj.getId()));

    if (n.getPublishedInId() != null) {
      ReferenceMapper rm = session.getMapper(ReferenceMapper.class);
      Reference ref = newKey(rm.get(obj.getDatasetKey(), obj.getId()));
      ref.setDatasetKey(datasetKey);
      rm.create(ref);

      n.setPublishedInId(ref.getId());
    }
    n.setDatasetKey(datasetKey);
    n.setOrigin(Origin.USER);
    nm.create(n);

    TaxonMapper tm = session.getMapper(TaxonMapper.class);
    Taxon t = new Taxon();
    t.setId(UUID.randomUUID().toString());
    t.setDatasetKey(datasetKey);
    t.setParentId(obj.getParentId());
    t.setName(n);
    t.setOrigin(Origin.USER);
    tm.create(t);

    session.commit();
    return t.getId();
  }

  private static <T extends VerbatimEntity & ID> T newKey(T e) {
    e.setVerbatimKey(null);
    e.setId(UUID.randomUUID().toString());
    return e;
  }

  @PUT
  @Path("{id}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void update(@PathParam("datasetKey") Integer datasetKey, @PathParam("id") String id, TreeNode obj,
                     @Auth ColUser user, @Context SqlSession session) {
    obj.setDatasetKey(datasetKey);
    obj.setId(id);
    //TODO...
    throw new NotImplementedException("update not implemented yet");
    //session.commit();
  }

  @DELETE
  @Path("{id}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void delete(@PathParam("datasetKey") Integer datasetKey, @PathParam("id") String id,
                     @Auth ColUser user, @Context SqlSession session) {
    throw new NotImplementedException("delete not implemented yet");
    //session.commit();
  }

  @GET
  public List<TreeNode> root(@PathParam("datasetKey") int datasetKey, @Context SqlSession session) {
    return session.getMapper(TreeMapper.class).root(datasetKey);
  }
  
  @GET
  @Path("{id}")
  public List<TreeNode> parents(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(TreeMapper.class).parents(datasetKey, id);
  }
  
  @GET
  @Path("{id}/children")
  public List<TreeNode> children(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(TreeMapper.class).children(datasetKey, id);
  }
  
}
