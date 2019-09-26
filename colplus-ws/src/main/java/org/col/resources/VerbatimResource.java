package org.col.resources;

import java.util.*;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.model.VerbatimRecord;
import org.col.api.vocab.Issue;
import org.col.db.mapper.LogicalOperator;
import org.col.db.mapper.VerbatimRecordMapper;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;
import org.gbif.dwc.terms.UnknownTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset/{datasetKey}/verbatim")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class VerbatimResource {
  private static final Set<String> KNOWN_PARAMS;
  static {
    Set<String> paras = new HashSet<>();
    paras.addAll(Page.PARAMETER_NAMES);
    paras.add("type");
    paras.add("issue");
    paras.add("termOp");
    paras.add("q");
    KNOWN_PARAMS = Collections.unmodifiableSet(paras);
  }
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(VerbatimResource.class);
  
  @GET
  public ResultPage<VerbatimRecord> list(@PathParam("datasetKey") int datasetKey,
                                         @QueryParam("type") List<Term> types,
                                         @QueryParam("termOp") @DefaultValue("AND") LogicalOperator termOp,
                                         @QueryParam("issue") List<Issue> issues,
                                         @QueryParam("q") String q,
                                         @Valid @BeanParam Page page,
                                         @Context UriInfo uri,
                                         @Context SqlSession session) {
    VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
    Map<Term, String> terms = termFilter(uri.getQueryParameters());
    
    return new ResultPage<VerbatimRecord>(page,
        mapper.count(datasetKey, types, terms, termOp, issues, q),
        mapper.list(datasetKey, types, terms, termOp, issues, q, page)
    );
  }
  
  private Map<Term, String> termFilter(MultivaluedMap<String, String> filter) {
    Map<Term, String> terms = new HashMap<>();
    if (filter != null) {
      for (String f : filter.keySet()) {
        if (KNOWN_PARAMS.contains(f)) continue;
        if (filter.getFirst(f) == null) continue;
        Term t = TermFactory.instance().findPropertyTerm(f);
        if (t instanceof UnknownTerm) {
          throw new IllegalArgumentException("Unknown term parameter" + f);
        }
        terms.put(t, filter.getFirst(f));
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
