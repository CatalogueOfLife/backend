package org.col.resources;

import com.codahale.metrics.annotation.Timed;
import org.apache.ibatis.session.SqlSession;
import org.col.api.*;
import org.col.dao.TaxonDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/taxon")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class TaxonResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(TaxonResource.class);

  @GET
  public ResultPage<Taxon> list(@QueryParam("datasetKey") Integer datasetKey,
      @QueryParam("root") Boolean root, @QueryParam("nameKey") Integer nameKey,
      @Valid @BeanParam Page page, @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    return dao.list(datasetKey, root, nameKey, page);
  }

  @GET
  @Timed
  @Path("{id}/{datasetKey}")
  public Integer lookupKey(@PathParam("id") String id, @PathParam("datasetKey") int datasetKey,
      @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    return dao.lookupKey(id, datasetKey);
  }

  @GET
  @Timed
  @Path("{key}")
  public Taxon get(@PathParam("key") int key, @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    return dao.get(key);
  }

  @GET
  @Timed
  @Path("{key}/children")
  public ResultPage<Taxon> children(@PathParam("key") int key, @Valid @BeanParam Page page,
      @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    return dao.getChildren(key, page);
  }

  @GET
  @Timed
  @Path("{key}/classification")
  public List<Taxon> classification(@PathParam("key") int key, @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    return dao.getClassification(key);
  }

  @GET
  @Timed
  @Path("{key}/info")
  public TaxonInfo info(@PathParam("key") int key, @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    return dao.getTaxonInfo(key);
  }

  @GET
  @Path("{key}/verbatim")
  public VerbatimRecord getVerbatim(@PathParam("key") int key, @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    return dao.getVerbatim(key);
  }

}
