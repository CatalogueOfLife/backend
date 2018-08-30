package org.col.resources;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.model.TermRecord;
import org.col.db.mapper.VerbatimRecordMapper;
import org.gbif.dwc.terms.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset/{datasetKey}/verbatim")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class VerbatimResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(VerbatimResource.class);

  @GET
  public ResultPage<TermRecord> list(@PathParam("dkey") int datasetKey,
                                     @QueryParam("type") Term type,
                                     @Valid @BeanParam Page page,
                                     @Context SqlSession session) {
    VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
    return new ResultPage<TermRecord>(page, mapper.count(datasetKey, type), mapper.list(datasetKey, type, page));
  }

  @GET
  @Path("{key}")
  public TermRecord get(@PathParam("dkey") int datasetKey, @PathParam("key") int key, @Context SqlSession session) {
    VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
    return mapper.get(datasetKey, key);
  }

}
