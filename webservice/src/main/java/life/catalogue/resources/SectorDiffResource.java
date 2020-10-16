package life.catalogue.resources;

import life.catalogue.api.model.DSID;
import life.catalogue.db.tree.SectorDiffService;

import javax.ws.rs.Path;

@Path("/dataset/{key}/sector/{id}/diff")
@SuppressWarnings("static-method")
public class SectorDiffResource extends AbstractDiffResource<DSID<Integer>> {

  public SectorDiffResource(SectorDiffService diff) {
    super(diff);
  }

  @Override
  DSID<Integer> keyFromPath(DSID<Integer> dsid) {
    return dsid;
  }
}
