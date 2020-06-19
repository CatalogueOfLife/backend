package life.catalogue.resources;

import life.catalogue.db.tree.SectorDiffService;

import javax.ws.rs.Path;

@Path("/dataset/{datasetKey}/sector/{key}/diff")
@SuppressWarnings("static-method")
public class SectorDiffResource extends AbstractDiffResource {

  public SectorDiffResource(SectorDiffService diff) {
    super(diff);
  }
}
