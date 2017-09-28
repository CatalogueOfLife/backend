package org.col.resources;

import com.codahale.metrics.annotation.Timed;
import org.apache.ibatis.session.SqlSession;
import org.col.api.*;
import org.col.db.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;


@Path("/dataset/{datasetKey}/name")
@Produces(MediaType.APPLICATION_JSON)
public class NameResource {
  private static final Logger LOG = LoggerFactory.getLogger(NameResource.class);

  @GET
  public PagingResultSet<Name> list(@PathParam("datasetKey") Integer datasetKey, @Context Page page, @Context SqlSession session) {
    NameMapper mapper = session.getMapper(NameMapper.class);
    return new PagingResultSet<Name>(page, mapper.count(datasetKey), mapper.list(datasetKey, page));
  }

  @GET
  @Timed
  @Path("{id}")
  public Name get(@PathParam("datasetKey") Integer datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    NameMapper mapper = session.getMapper(NameMapper.class);
    return mapper.get(datasetKey, id);
  }

  @GET
  @Timed
  @Path("{id}/synonyms")
  public List<Name> getSynonyms(@PathParam("datasetKey") Integer datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    NameMapper mapper = session.getMapper(NameMapper.class);
    return mapper.synonyms(datasetKey, id);
  }

  @GET
  @Timed
  @Path("{id}/publishedIn")
  public Reference getPublishedIn(@PathParam("datasetKey") Integer datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
    return mapper.getPublishedIn(datasetKey, id);
  }

  @GET
  @Timed
  @Path("{id}/verbatim")
  public VerbatimRecord getVerbatim(@PathParam("datasetKey") Integer datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
    return mapper.getByName(datasetKey, id);
  }

  @GET
  @Timed
  @Path("{id}/acts")
  public List<NameAct> getActs(@PathParam("datasetKey") Integer datasetKey, @PathParam("id") String id, @QueryParam("homotypic") Boolean homotypic, @Context SqlSession session) {
    NameActMapper mapper = session.getMapper(NameActMapper.class);
    return homotypic ? mapper.listByHomotypicGroup(datasetKey, id) : mapper.listByName(datasetKey, id);
  }

}
