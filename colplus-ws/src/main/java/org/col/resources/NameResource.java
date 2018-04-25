package org.col.resources;

import com.codahale.metrics.annotation.Timed;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.*;
import org.col.db.dao.NameDao;
import org.col.db.dao.NameUsageDao;
import org.col.db.mapper.NameActMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/name")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class NameResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameResource.class);

  @GET
  public ResultPage<Name> list(@QueryParam("datasetKey") Integer datasetKey,
      @Valid @BeanParam Page page, @Context SqlSession session) {
    NameDao dao = new NameDao(session);
    return dao.list(datasetKey, page);
  }

  @GET
  @Timed
  @Path("search")
  public ResultPage<NameUsage> search(@BeanParam NameSearch query,
      @Valid @BeanParam Page page, @Context SqlSession session) {
    NameUsageDao dao = new NameUsageDao(session);
    return dao.search(query, page);
  }

  @GET
  @Path("{id}/{datasetKey}")
  public Integer lookupKey(@PathParam("id") String id, @PathParam("datasetKey") int datasetKey,
      @Context SqlSession session) {
    NameDao dao = new NameDao(session);
    return dao.lookupKey(id, datasetKey);
  }

  @GET
  @Path("{key}")
  public Name get(@PathParam("key") int key, @Context SqlSession session) {
    NameDao dao = new NameDao(session);
    return dao.get(key);
  }

  @GET
  @Timed
  @Path("{key}/listByTaxon")
  public List<Name> getSynonyms(@PathParam("key") int key, @Context SqlSession session) {
    NameDao dao = new NameDao(session);
    return dao.homotypicGroup(key);
  }

  @GET
  @Path("{key}/verbatim")
  public VerbatimRecord getVerbatim(@PathParam("key") int key, @Context SqlSession session) {
    NameDao dao = new NameDao(session);
    return dao.getVerbatim(key);
  }

  @GET
  @Timed
  @Path("{key}/acts")
  public List<NameAct> getActs(@PathParam("key") int key, @Context SqlSession session) {
    NameActMapper mapper = session.getMapper(NameActMapper.class);
    return mapper.list(key);
  }

}
