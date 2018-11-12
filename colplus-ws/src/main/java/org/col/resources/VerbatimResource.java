package org.col.resources;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.model.VerbatimRecord;
import org.col.api.vocab.Issue;
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
  public ResultPage<VerbatimRecord> list(@PathParam("datasetKey") int datasetKey,
                                         @QueryParam("type") Term type,
                                         @QueryParam("issue") Issue issue,
                                         @Valid @BeanParam Page page,
                                         @Context SqlSession session) {
    VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
    return new ResultPage<VerbatimRecord>(page, mapper.count(datasetKey, type), mapper.list(datasetKey, type, issue, page));
  }
  
  @GET
  @Path("{key}")
  public VerbatimRecord get(@PathParam("datasetKey") int datasetKey, @PathParam("key") int key, @Context SqlSession session) {
    VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
    return mapper.get(datasetKey, key);
  }
  
}
