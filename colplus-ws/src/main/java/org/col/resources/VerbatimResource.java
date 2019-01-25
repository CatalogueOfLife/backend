package org.col.resources;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.model.VerbatimRecord;
import org.col.api.vocab.Issue;
import org.col.common.text.StringUtils;
import org.col.db.mapper.VerbatimRecordMapper;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;
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
                                         @QueryParam("type") List<Term> types,
                                         @QueryParam("term") List<String> filter,
                                         @QueryParam("issue") List<Issue> issues,
                                         @Valid @BeanParam Page page,
                                         @Context SqlSession session) {
    VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
    Map<Term, String> terms = termFilter(filter);
    
    return new ResultPage<VerbatimRecord>(page,
        mapper.count(datasetKey, types, terms, issues),
        mapper.list(datasetKey, types, terms, issues, page)
    );
  }
  
  private Map<Term, String> termFilter(List<String> filter) {
    Map<Term, String> terms = new HashMap<>();
    if (filter != null) {
      for (String f : filter) {
        String[] parts = StringUtils.splitRight(f, ':');
        if (parts == null) {
          throw new IllegalArgumentException("Term query parameter must contain a colon delimited value");
        }
        terms.put(TermFactory.instance().findPropertyTerm(parts[0]), parts[1]);
      }
    }
    return terms;
  }
  
  @GET
  @Path("{key}")
  public VerbatimRecord get(@PathParam("datasetKey") int datasetKey, @PathParam("key") int key, @Context SqlSession session) {
    VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
    return mapper.get(datasetKey, key);
  }
  
}
