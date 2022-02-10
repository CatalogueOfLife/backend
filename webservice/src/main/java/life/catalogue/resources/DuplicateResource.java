package life.catalogue.resources;

import life.catalogue.api.model.Duplicate;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.MatchingMode;
import life.catalogue.api.vocab.NameCategory;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.dao.DuplicateDao;
import life.catalogue.dw.jersey.MoreMediaTypes;
import life.catalogue.dw.jersey.filter.VaryAccept;

import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset/{key}/duplicate")
@Produces(MediaType.APPLICATION_JSON)
public class DuplicateResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DuplicateResource.class);
  
  
  @GET
  @VaryAccept
  public List<Duplicate> find(@PathParam("key") int datasetKey,
                              @QueryParam("entity") EntityType entity,
                              @QueryParam("mode") MatchingMode mode,
                              @QueryParam("minSize") Integer minSize,
                              @QueryParam("sourceDatasetKey") Integer sourceDatasetKey,
                              @QueryParam("sectorKey") Integer sectorKey,
                              @QueryParam("category") NameCategory category,
                              @QueryParam("rank") Set<Rank> ranks,
                              @QueryParam("status") Set<TaxonomicStatus> status,
                              @QueryParam("acceptedDifferent") Boolean acceptedDifferent,
                              @QueryParam("authorshipDifferent") Boolean authorshipDifferent,
                              @QueryParam("rankDifferent") Boolean rankDifferent,
                              @QueryParam("codeDifferent") Boolean codeDifferent,
                              @QueryParam("withDecision") Boolean withDecision,
                              @QueryParam("catalogueKey") Integer catalogueKey,
                              @Valid @BeanParam Page page, @Context SqlSession session) {
    var req = new DuplicateDao.DuplicateRequest(entity, mode, minSize, datasetKey, sourceDatasetKey, sectorKey, category, ranks, status,
      authorshipDifferent, acceptedDifferent, rankDifferent, codeDifferent, withDecision, catalogueKey);

    DuplicateDao dao = new DuplicateDao(session);
    return dao.find(req, page);
  }

  @GET
  @VaryAccept
  @Produces({MoreMediaTypes.TEXT_CSV, MoreMediaTypes.TEXT_TSV})
  public Stream<Object[]> download(@PathParam("key") int datasetKey,
                                   @QueryParam("entity") EntityType entity,
                                   @QueryParam("mode") MatchingMode mode,
                                   @QueryParam("minSize") Integer minSize,
                                   @QueryParam("sourceDatasetKey") Integer sourceDatasetKey,
                                   @QueryParam("sectorKey") Integer sectorKey,
                                   @QueryParam("category") NameCategory category,
                                   @QueryParam("rank") Set<Rank> ranks,
                                   @QueryParam("status") Set<TaxonomicStatus> status,
                                   @QueryParam("acceptedDifferent") Boolean acceptedDifferent,
                                   @QueryParam("authorshipDifferent") Boolean authorshipDifferent,
                                   @QueryParam("rankDifferent") Boolean rankDifferent,
                                   @QueryParam("codeDifferent") Boolean codeDifferent,
                                   @QueryParam("withDecision") Boolean withDecision,
                                   @QueryParam("catalogueKey") Integer catalogueKey,
                                   @Context SqlSession session) {
    var req = new DuplicateDao.DuplicateRequest(entity, mode, minSize, datasetKey, sourceDatasetKey, sectorKey, category, ranks, status,
      authorshipDifferent, acceptedDifferent, rankDifferent, codeDifferent, withDecision, catalogueKey);

    DuplicateDao dao = new DuplicateDao(session);
    return dao.list(req);
  }
  
}
