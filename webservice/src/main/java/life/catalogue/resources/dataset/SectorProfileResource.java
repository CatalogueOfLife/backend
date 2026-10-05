package life.catalogue.resources.dataset;

import life.catalogue.api.model.*;
import life.catalogue.api.search.QuerySearchRequest;
import life.catalogue.api.search.SectorSearchRequest;
import life.catalogue.dao.SectorDao;
import life.catalogue.dao.SectorProfileDao;

import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * Sector profiles: sync settings for every sector of a project a selector matches.
 * See docs/SECTOR-SETTINGS.md.
 */
@Path("/dataset/{key}/sector/profile")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class SectorProfileResource extends AbstractDatasetScopedResource<Integer, SectorProfile, QuerySearchRequest> {
  private final SectorDao sdao;

  public SectorProfileResource(SectorProfileDao dao, SectorDao sdao) {
    super(SectorProfile.class, dao);
    this.sdao = sdao;
  }

  /**
   * The sectors the profile selects right now, i.e. the ones it applies to on their next sync.
   */
  @GET
  @Path("{id}/sector")
  public ResultPage<Sector> sectors(@PathParam("key") int datasetKey, @PathParam("id") int id, @Valid @BeanParam Page page) {
    var req = SectorSearchRequest.byProject(datasetKey);
    req.setProfileKey(id);
    return sdao.search(req, page);
  }
}
