package life.catalogue.resources;

import life.catalogue.api.model.DSID;
import life.catalogue.db.tree.DatasetDiffService;

import javax.ws.rs.Path;

@Path("/dataset/{datasetKey}/diff")
@SuppressWarnings("static-method")
public class DatasetDiffResource extends AbstractDiffResource<Integer> {

  public DatasetDiffResource(DatasetDiffService diff) {
    super(diff);
  }

  @Override
  Integer keyFromPath(DSID<Integer> dsid) {
    return dsid.getDatasetKey();
  }
}
