package org.col.resources;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.col.api.Page;
import org.col.api.PagingResultSet;
import org.col.api.Reference;
import org.col.db.mapper.ReferenceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.annotation.Timed;


@Path("/dataset/{datasetKey}/reference")
@Produces(MediaType.APPLICATION_JSON)
public class ReferenceResource {
  private static final Logger LOG = LoggerFactory.getLogger(ReferenceResource.class);

  @GET
  public PagingResultSet<Reference> list(@PathParam("datasetKey") Integer datasetKey, @Context Page page, @Context SqlSession session) {
    ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
    return new PagingResultSet<Reference>(page, mapper.count(datasetKey), mapper.list(datasetKey, page));
  }

  @GET
  @Timed
  @Path("{id}")
  public Reference get(@PathParam("datasetKey") Integer datasetKey, @PathParam("id") String key, @Context SqlSession session) {
    ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
    return mapper.get(datasetKey, key);
  }

}
