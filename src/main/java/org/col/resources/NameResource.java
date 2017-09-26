package org.col.resources;

import com.codahale.metrics.annotation.Timed;
import org.apache.ibatis.session.SqlSession;
import org.col.api.Name;
import org.col.api.NameAct;
import org.col.api.Reference;
import org.col.db.mapper.NameActMapper;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.ReferenceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;


@Path("{datasetKey}/name")
@Produces(MediaType.APPLICATION_JSON)
public class NameResource {
  private static final Logger LOG = LoggerFactory.getLogger(NameResource.class);

  @GET
  @Timed
  @Path("{key}")
  public Name get(@PathParam("datasetKey") Integer datasetKey, @PathParam("key") String key, @Context SqlSession session) {
    NameMapper mapper = session.getMapper(NameMapper.class);
    return mapper.get(datasetKey, key);
  }

  @GET
  @Timed
  @Path("{key}/synonyms")
  public Name getSynonyms(@PathParam("datasetKey") Integer datasetKey, @PathParam("key") String key, @Context SqlSession session) {
    NameMapper mapper = session.getMapper(NameMapper.class);
    return mapper.get(datasetKey, key);
  }

  @GET
  @Timed
  @Path("{key}/publishedIn")
  public Reference getPublishedIn(@PathParam("datasetKey") Integer datasetKey, @PathParam("key") String key, @Context SqlSession session) {
    ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
    return mapper.get(datasetKey, key);
  }

  @GET
  @Timed
  @Path("{key}/acts")
  public List<NameAct> getActs(@PathParam("datasetKey") Integer datasetKey, @PathParam("key") String key, @QueryParam("homotypic") Boolean homotypic, @Context SqlSession session) {
    NameActMapper mapper = session.getMapper(NameActMapper.class);
    return homotypic ? mapper.listByHomotypicGroup(datasetKey, key) : mapper.listByName(datasetKey, key);
  }

}
