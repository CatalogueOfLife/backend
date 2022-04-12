package life.catalogue.resources;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.User;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.db.tree.DatasetDiffService;

import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.List;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import io.dropwizard.auth.Auth;

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
  @Path("test")
  @Produces(MediaType.TEXT_PLAIN)
  public Reader test() throws IOException {
    return UTF8IoUtils.readerFromFile(new File("/Users/markus/Downloads/gen-wsc.sh"));
  }


  @GET
  @Path("{key2}")
  @Produces(MediaType.TEXT_PLAIN)
  public Reader diffNames(@PathParam("key") Integer key,
                          @PathParam("key2") Integer key2,
                          @QueryParam("root") List<String> root,
                          @QueryParam("root2") List<String> root2,
                          @QueryParam("minRank") Rank lowestRank,
                          @QueryParam("authorship") @DefaultValue("true") boolean inclAuthorship,
                          @QueryParam("synonyms") boolean inclSynonyms,
                          @QueryParam("showParent") boolean showParent,
                          @QueryParam("parentRank") Rank parentRank,
                          @Auth User user) throws IOException {
    return service.datasetNamesDiff(user.getKey(), key, root, key2, root2, lowestRank, inclAuthorship, inclSynonyms, showParent, parentRank);
  }

}
