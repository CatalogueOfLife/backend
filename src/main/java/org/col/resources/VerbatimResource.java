package org.col.resources;

import com.codahale.metrics.annotation.Timed;
import org.apache.ibatis.session.SqlSession;
import org.col.api.Page;
import org.col.api.PagingResultSet;
import org.col.api.VerbatimRecord;
import org.col.db.mapper.VerbatimRecordMapper;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;


@Path("/dataset/{datasetKey}/verbatim")
@Produces(MediaType.APPLICATION_JSON)
public class VerbatimResource {

  @GET
  @Timed
  public PagingResultSet<VerbatimRecord > list(@PathParam("datasetKey") Integer datasetKey, @Context Page page, @Context SqlSession session) {
    VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
    return new PagingResultSet<VerbatimRecord>(page, mapper.count(datasetKey), mapper.list(datasetKey, page));
  }

  @GET
  @Timed
  @Path("{key}")
  public VerbatimRecord get(@PathParam("datasetKey") Integer datasetKey, @PathParam("key") String id, @Context SqlSession session) {
    VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
    return mapper.get(datasetKey, id);
  }

}
