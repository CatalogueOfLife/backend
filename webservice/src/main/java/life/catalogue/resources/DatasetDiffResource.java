package life.catalogue.resources;

import io.dropwizard.auth.Auth;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DSIDValue;
import life.catalogue.api.model.User;
import life.catalogue.db.tree.BaseDiffService;
import life.catalogue.db.tree.DatasetDiffService;

import org.apache.commons.lang3.NotImplementedException;
import org.checkerframework.checker.units.qual.K;

import org.gbif.nameparser.api.Rank;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

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
  @Produces(MediaType.TEXT_PLAIN)
  public Reader diffNames(@PathParam("key") Integer key,
                          @PathParam("key2") Integer key2,
                          @QueryParam("root") List<String> root,
                          @QueryParam("root2") List<String> root2,
                          @QueryParam("minRank") Rank lowestRank,
                          @QueryParam("synonyms") boolean inclSynonyms,
                          @Auth User user) throws IOException {
    return service.datasetNamesDiff(user.getKey(), key, root, key2, root2, lowestRank, inclSynonyms);
  }
}
