package org.col.resources;

import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.*;
import org.col.db.dao.TaxonDao;
import org.col.dw.jersey.exception.NotFoundException;
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
    return dao.list(datasetKey, root, page);
  }

  @GET
  @Path("id/{id}")
  public Integer lookupKey(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    Integer key = dao.lookupKey(id, datasetKey);
    if (key == null) {
      throw NotFoundException.idNotFound(Taxon.class, datasetKey, id);
    }
    return key;
  }

  @GET
  @Path("{key}")
  public Taxon get(@PathParam("datasetKey") int datasetKey, @PathParam("key") int key, @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    Taxon t = dao.get(datasetKey, key);
    if (t == null) {
      throw NotFoundException.keyNotFound(Taxon.class, key);
    }
    return dao.get(datasetKey, key);
  }

  @GET
  @Path("{key}/children")
  public ResultPage<Taxon> children(@PathParam("datasetKey") int datasetKey, @PathParam("key") int key, @Valid @BeanParam Page page,
      @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    return dao.getChildren(datasetKey, key, page);
  }

  @GET
  @Path("{key}/synonyms")
  public Synonymy synonyms(@PathParam("datasetKey") int datasetKey, @PathParam("key") int key, @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    return dao.getSynonymy(datasetKey, key);
  }

  @GET
  @Path("{key}/classification")
  public List<Taxon> classification(@PathParam("datasetKey") int datasetKey, @PathParam("key") int key, @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    return dao.getClassification(datasetKey, key);
  }

  @GET
  @Timed
  @Path("{key}/info")
  public TaxonInfo info(@PathParam("datasetKey") int datasetKey, @PathParam("key") int key, @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    TaxonInfo info = dao.getTaxonInfo(datasetKey, key);
    if (info == null) {
      throw NotFoundException.keyNotFound(Taxon.class, key);
    }
    return info;
  }

}
