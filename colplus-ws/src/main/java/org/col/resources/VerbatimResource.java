package org.col.resources;

import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.*;
import org.col.db.dao.NameDao;
import org.col.db.dao.NameUsageDao;
import org.col.db.mapper.NameActMapper;
import org.col.db.mapper.VerbatimRecordMapper;
import org.gbif.dwc.terms.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/verbatim")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class VerbatimResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(VerbatimResource.class);

  @GET
  public ResultPage<TermRecord> list(@QueryParam("datasetKey") Integer datasetKey,
                                     @QueryParam("type") Term type,
                                     @Valid @BeanParam Page page,
                                     @Context SqlSession session) {
    VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
    return new ResultPage<TermRecord>(page, mapper.count(datasetKey, type), mapper.list(datasetKey, type, page));
  }

  @GET
  @Path("{key}")
  public TermRecord get(@PathParam("key") int key, @Context SqlSession session) {
    VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
    return mapper.get(key);
  }

}
