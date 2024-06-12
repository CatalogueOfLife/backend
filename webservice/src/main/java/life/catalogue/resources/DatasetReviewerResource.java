package life.catalogue.resources;

import life.catalogue.api.model.User;
import life.catalogue.dao.AuthorizationDao;

import jakarta.ws.rs.Path;

@Path("/dataset/{key}/reviewer")
@SuppressWarnings("static-method")
public class DatasetReviewerResource extends AbstractDatasetUserResource {

  public DatasetReviewerResource(AuthorizationDao dao) {
    super(User.Role.REVIEWER, dao);
  }

}
