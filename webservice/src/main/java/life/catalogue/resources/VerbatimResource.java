package life.catalogue.resources;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Issue;
import life.catalogue.db.mapper.LogicalOperator;
import life.catalogue.db.mapper.VerbatimRecordMapper;

import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;
import org.gbif.dwc.terms.UnknownTerm;

import java.util.*;

import javax.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset/{key}/verbatim")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class VerbatimResource {
  private static final Set<String> KNOWN_PARAMS;
  static {
    Set<String> paras = new HashSet<>(Page.PARAMETER_NAMES);
    paras.add("type");
    paras.add("term");
    paras.add("termOp");
    paras.add("issue");
    paras.add("q");
    KNOWN_PARAMS = Collections.unmodifiableSet(paras);
  }
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(VerbatimResource.class);
  
  @GET
  public ResultPage<VerbatimRecord> list(@PathParam("key") int datasetKey,
                                         @QueryParam("type") List<Term> types,
                                         @QueryParam("term") List<Term> terms,
                                         @QueryParam("termOp") @DefaultValue("AND") LogicalOperator termOp,
                                         @QueryParam("issue") List<Issue> issues,
                                         @QueryParam("q") String q,
                                         @Valid @BeanParam Page page,
                                         @Context UriInfo uri,
                                         @Context SqlSession session) {
    Map<Term, String> termValues = termFilter(uri.getQueryParameters());
    VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
    return new ResultPage<>(page,
        mapper.count(datasetKey, types, termValues, termOp, terms, issues, q),
        mapper.list(datasetKey, types, termValues, termOp, terms, issues, q, page)
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
          throw new IllegalArgumentException("Unknown term parameter " + f);
        }
        terms.put(t, filter.getFirst(f));
      }
    }
    return terms;
  }
  
  @GET
  @Path("{id}")
  public VerbatimRecord get(@PathParam("key") int datasetKey, @PathParam("id") int id, @Context SqlSession session) {
    VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
    return mapper.get(DSID.of(datasetKey, id));
  }
  
}
