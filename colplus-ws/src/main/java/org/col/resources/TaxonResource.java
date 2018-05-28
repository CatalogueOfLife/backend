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
import org.col.db.mapper.VerbatimRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/taxon")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class TaxonResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(TaxonResource.class);

  @GET
  public ResultPage<Taxon> list(@QueryParam("datasetKey") Integer datasetKey,
                                @QueryParam("root") boolean root,
                                @Valid @BeanParam Page page, @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    return dao.list(datasetKey, root, page);
  }

  @GET
  @Path("{id}/{datasetKey}")
  public Integer lookupKey(@PathParam("id") String id, @PathParam("datasetKey") int datasetKey,
      @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    return dao.lookupKey(id, datasetKey);
  }

  @GET
  @Path("{key}")
  public Taxon get(@PathParam("key") int key, @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    return dao.get(key);
  }

  @GET
  @Path("{key}/children")
  public ResultPage<Taxon> children(@PathParam("key") int key, @Valid @BeanParam Page page,
      @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    return dao.getChildren(key, page);
  }

  @GET
  @Path("{key}/synonyms")
  public Synonymy synonyms(@PathParam("key") int key, @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    return dao.getSynonymy(key);
  }

  @GET
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

}
