package life.catalogue.resources.dataset;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import life.catalogue.api.model.DatasetBreakdown;
import life.catalogue.dao.TaxonDao;
import life.catalogue.es.search.NameUsageSearchService;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Produces(MediaType.APPLICATION_JSON)
@Path("/dataset/{key}/breakdown")
public class DatasetBreakdownResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetBreakdownResource.class);
  private final TaxonDao dao;

  public DatasetBreakdownResource(TaxonDao dao) {
    this.dao = dao;
  }

  @GET
  public DatasetBreakdown breakdown(@PathParam("key") int datasetKey,
                                    @QueryParam("rank") Rank rank,
                                    @QueryParam("inclSynonyms") boolean inclSynonyms
  ) {
    return dao.breakdown(datasetKey, rank, inclSynonyms);
  }
}
