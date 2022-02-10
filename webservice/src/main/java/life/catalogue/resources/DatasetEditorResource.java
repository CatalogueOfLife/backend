package life.catalogue.resources;

import life.catalogue.api.model.User;
import life.catalogue.dao.AuthorizationDao;

import javax.ws.rs.Path;

@Path("/dataset/{key}/editor")
@SuppressWarnings("static-method")
public class DatasetEditorResource extends AbstractDatasetUserResource {

  public DatasetEditorResource(AuthorizationDao dao) {
    super(User.Role.EDITOR, dao);
  }

}
