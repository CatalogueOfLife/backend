package life.catalogue.resources.dataset;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DSIDValue;
import life.catalogue.printer.SectorDiffService;

import java.io.IOException;
import java.io.Reader;

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
