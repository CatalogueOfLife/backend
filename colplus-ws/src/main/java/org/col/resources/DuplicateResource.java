package org.col.resources;

import java.util.List;
import java.util.Set;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Duplicate;
import org.col.api.model.Page;
import org.col.api.vocab.MatchingMode;
import org.col.api.vocab.TaxonomicStatus;
import org.col.dao.DuplicateDao;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset/{datasetKey}/duplicate")
@Produces(MediaType.APPLICATION_JSON)
public class DuplicateResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DuplicateResource.class);
  
  
  public DuplicateResource() {
  }
  
  @GET
  public List<Duplicate> find(@PathParam("datasetKey") int datasetKey,
                              @QueryParam("mode") MatchingMode mode,
                              @QueryParam("minSize") Integer minSize,
                              @QueryParam("sectorKey") Integer sectorKey,
                              @QueryParam("rank") Rank rank,
                              @QueryParam("status") Set<TaxonomicStatus> status,
                              @QueryParam("parentDifferent") Boolean parentDifferent,
                              @QueryParam("authorshipDifferent") Boolean authorshipDifferent,
                              @QueryParam("withDecision") Boolean withDecision,
                              @Valid @BeanParam Page page, @Context SqlSession session) {
    DuplicateDao dao = new DuplicateDao(session);
    return dao.find(mode, minSize, datasetKey, sectorKey, rank, status, authorshipDifferent, parentDifferent, withDecision, page);
  }
  
}
