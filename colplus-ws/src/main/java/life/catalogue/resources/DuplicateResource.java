package life.catalogue.resources;

import java.util.List;
import java.util.Set;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import life.catalogue.api.model.Duplicate;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.MatchingMode;
import life.catalogue.api.vocab.NameCategory;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.dao.DuplicateDao;
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
                              @QueryParam("entity") EntityType entity,
                              @QueryParam("mode") MatchingMode mode,
                              @QueryParam("minSize") Integer minSize,
                              @QueryParam("sectorKey") Integer sectorKey,
                              @QueryParam("category") NameCategory category,
                              @QueryParam("rank") Set<Rank> ranks,
                              @QueryParam("status") Set<TaxonomicStatus> status,
                              @QueryParam("acceptedDifferent") Boolean acceptedDifferent,
                              @QueryParam("authorshipDifferent") Boolean authorshipDifferent,
                              @QueryParam("rankDifferent") Boolean rankDifferent,
                              @QueryParam("codeDifferent") Boolean codeDifferent,
                              @QueryParam("withDecision") Boolean withDecision,
                              @Valid @BeanParam Page page, @Context SqlSession session) {
    DuplicateDao dao = new DuplicateDao(session);
    if (entity == null || entity == EntityType.NAME_USAGE) {
        return dao.findUsages(mode, minSize, datasetKey, sectorKey, category, ranks, status, authorshipDifferent, acceptedDifferent, rankDifferent, codeDifferent, withDecision, page);
      
    } else if (entity == EntityType.NAME) {
        return dao.findNames(mode, minSize, datasetKey, category, ranks, authorshipDifferent, rankDifferent, codeDifferent, page);
    }
    throw new IllegalArgumentException("Duplicates only supported for NAME or NAME_USAGE entity");
  }
  
}
