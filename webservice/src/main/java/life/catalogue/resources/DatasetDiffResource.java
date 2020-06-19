package life.catalogue.resources;

import life.catalogue.db.tree.DatasetDiffService;

import javax.ws.rs.Path;

@Path("/dataset/{key}/diff")
@SuppressWarnings("static-method")
public class DatasetDiffResource extends AbstractDiffResource {

  public DatasetDiffResource(DatasetDiffService diff) {
    super(diff);
  }
}
