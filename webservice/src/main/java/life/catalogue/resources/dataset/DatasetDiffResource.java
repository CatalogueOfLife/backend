package life.catalogue.resources.dataset;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.User;
import life.catalogue.printer.DatasetDiffService;
import life.catalogue.printer.NamesDiff;

import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Set;

import io.dropwizard.auth.Auth;
import jakarta.ws.rs.*;

@Path("/dataset/{key}/diff")
@SuppressWarnings("static-method")
public class DatasetDiffResource extends AbstractDiffResource<Integer> {
  private final DatasetDiffService service;

  public DatasetDiffResource(DatasetDiffService diff) {
    super(diff);
    service = diff;
  }

  @Override
  Integer keyFromPath(DSID<Integer> dsid) {
    return dsid.getDatasetKey();
  }

  @GET
  @Path("{key2}")
  public NamesDiff diffNames(@PathParam("key") Integer key,
                             @PathParam("key2") Integer key2,
                             @QueryParam("root") List<String> root,
                             @QueryParam("root2") List<String> root2,
                             @QueryParam("minRank") Rank lowestRank,
                             @QueryParam("authorship") @DefaultValue("true") boolean inclAuthorship,
                             @QueryParam("synonyms") boolean inclSynonyms,
                             @QueryParam("showParent") boolean showParent,
                             @QueryParam("parentRank") Rank parentRank,
                             @QueryParam("rankFilter") Set<Rank> rankFilter,
                             @Auth User user) {
    return service.datasetNamesDiff(user.getKey(), key, root, key2, root2, lowestRank, inclAuthorship, inclSynonyms, showParent, parentRank, rankFilter);
  }

}
