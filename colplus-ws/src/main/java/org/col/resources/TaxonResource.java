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
  @Path("{id}")
  public Taxon get(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    Taxon t = dao.get(datasetKey, id);
    if (t == null) {
      throw NotFoundException.idNotFound(Taxon.class, datasetKey, id);
    }
    return dao.get(datasetKey, id);
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
