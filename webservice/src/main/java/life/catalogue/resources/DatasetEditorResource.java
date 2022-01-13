package life.catalogue.resources;

import life.catalogue.api.model.*;
import life.catalogue.dao.AuthorizationDao;
import life.catalogue.dao.DatasetDao;
import life.catalogue.db.mapper.UserMapper;
import life.catalogue.dw.auth.Roles;

import java.util.List;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.auth.Auth;

@Path("/dataset/{key}/editor")
@SuppressWarnings("static-method")
public class DatasetEditorResource extends AbstractDatasetUserResource {

  public DatasetEditorResource(AuthorizationDao dao) {
    super(User.Role.EDITOR, dao);
  }

}
