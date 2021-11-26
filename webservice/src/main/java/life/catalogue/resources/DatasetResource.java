package life.catalogue.resources;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.assembly.AssemblyCoordinator;
import life.catalogue.assembly.AssemblyState;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.DatasetSourceDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.UserMapper;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.MoreMediaTypes;
import life.catalogue.dw.jersey.filter.VaryAccept;
import life.catalogue.img.ImageService;
import life.catalogue.img.ImageServiceFS;
import life.catalogue.img.ImgConfig;
import life.catalogue.release.ReleaseManager;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Hidden;

import static life.catalogue.api.model.User.userkey;

@Path("/dataset")
@SuppressWarnings("static-method")
public class DatasetResource extends AbstractGlobalResource<Dataset> {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetResource.class);
  private final DatasetDao dao;
  private final DatasetSourceDao sourceDao;
  private final AssemblyCoordinator assembly;
  private final ReleaseManager releaseManager;

  public DatasetResource(SqlSessionFactory factory, DatasetDao dao, DatasetSourceDao sourceDao, AssemblyCoordinator assembly, ReleaseManager releaseManager) {
    super(Dataset.class, dao, factory);
    this.dao = dao;
    this.sourceDao = sourceDao;
    this.assembly = assembly;
    this.releaseManager = releaseManager;
  }

  /**
   * @return the primary key of the object. Together with the CreatedResponseFilter will return a 201 location
   */
  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML, MoreMediaTypes.APP_YAML, MoreMediaTypes.TEXT_YAML})
  public Integer createAlt(Dataset obj, @Auth User user) {
    return this.create(obj, user);
  }

  @GET
  @VaryAccept
  public ResultPage<Dataset> search(@Valid @BeanParam Page page, @BeanParam DatasetSearchRequest req, @Auth Optional<User> user) {
    return dao.search(req, userkey(user), page);
  }

  @GET
  @Path("{key}")
  @Override
  @VaryAccept
  @Produces({MediaType.APPLICATION_JSON,
    MediaType.APPLICATION_XML, MediaType.TEXT_XML,
    MoreMediaTypes.APP_YAML, MoreMediaTypes.TEXT_YAML,
    MoreMediaTypes.APP_JSON_CSL,
    MoreMediaTypes.APP_BIBTEX
  })
  public Dataset get(@PathParam("key") Integer key) {
    return super.get(key);
  }

  @PUT
  @Path("{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML, MoreMediaTypes.APP_YAML, MoreMediaTypes.TEXT_YAML})
  public void updateAlt(@PathParam("key") Integer key, Dataset obj, @Auth User user) {
    this.update(key, obj, user);
  }

  @GET
  @Path("{key}/{attempt}")
  @VaryAccept
  @Produces({MediaType.APPLICATION_JSON,
    MediaType.APPLICATION_XML, MediaType.TEXT_XML,
    MoreMediaTypes.APP_YAML, MoreMediaTypes.TEXT_YAML,
    MoreMediaTypes.APP_JSON_CSL,
    MoreMediaTypes.APP_BIBTEX
  })
  public Dataset getArchive(@PathParam("key") Integer key, @PathParam("attempt") Integer attempt) {
    return dao.getArchive(key, attempt);
  }

  @GET
  @Path("{key}/settings")
  public DatasetSettings getSettings(@PathParam("key") int key) {
    return dao.getSettings(key);
  }

  @PUT
  @Path("{key}/settings")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void putSettings(@PathParam("key") int key, DatasetSettings settings, @Auth User user) {
    dao.putSettings(key, settings, user.getKey());
  }

  @GET
  @Path("{key}/assembly")
  public AssemblyState assemblyState(@PathParam("key") int key) {
    return assembly.getState(key);
  }

  @POST
  @Path("/{key}/copy")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Integer copy(@PathParam("key") int key, @Auth User user) {
    return releaseManager.duplicate(key, user);
  }

  @POST
  @Path("/{key}/release")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Integer release(@PathParam("key") int key, @Auth User user) {
    return releaseManager.release(key, user);
  }

  @GET
  @Path("{key}/editor")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public List<User> editors(@PathParam("key") int key, @Auth User user, @Context SqlSession session) {
    return session.getMapper(UserMapper.class).datasetEditors(key);
  }

  @POST
  @Path("/{key}/editor")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void addEditor(@PathParam("key") int key, int editorKey, @Auth User user) {
    dao.addEditor(key, editorKey, user);
  }

  @DELETE
  @Path("/{key}/editor/{id}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void removeEditor(@PathParam("key") int key, @PathParam("id") int editorKey, @Auth User user) {
    dao.removeEditor(key, editorKey, user);
  }

  @GET
  @Path("/{key}/contribution")
  public ProjectContribution projectContribution(@PathParam("key") int datasetKey, @Context SqlSession session) {
    ProjectContribution contrib = new ProjectContribution();
    DatasetMapper dm = session.getMapper(DatasetMapper.class);
    contrib.add(dm.get(datasetKey), false);
    sourceDao.list(datasetKey,null, false).forEach(src -> contrib.add(src, true));

    return contrib;
  }

  @GET
  @Path("/{key}/source")
  public List<Dataset> projectSources(@PathParam("key") int datasetKey, @QueryParam("notCurrentOnly") boolean notCurrentOnly) {
    var ds = sourceDao.list(datasetKey, null, false);
    if (notCurrentOnly) {
      List<Dataset> notCurrent = new ArrayList<>();
      for (Dataset d : ds) {
        Dataset curr = dao.get(d.getKey());
        if (curr != null && !Objects.equals(curr.getAttempt(), d.getAttempt())) {
          notCurrent.add(d);
        }
      }
      return notCurrent;

    } else {
      return ds;
    }
  }

  @GET
  @Path("/{key}/source/{id}")
  @VaryAccept
  @Produces({MediaType.APPLICATION_JSON,
    MediaType.APPLICATION_XML, MediaType.TEXT_XML,
    MoreMediaTypes.APP_YAML, MoreMediaTypes.TEXT_YAML,
    MoreMediaTypes.APP_JSON_CSL,
    MoreMediaTypes.APP_BIBTEX
  })
  public Dataset projectSource(@PathParam("key") int datasetKey,
                               @PathParam("id") int id,
                               @QueryParam("original") boolean original) {
    return sourceDao.get(datasetKey, id, original);
  }

  @PUT
  @Path("/{key}/source/{id}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  @Consumes({MediaType.APPLICATION_JSON, MoreMediaTypes.APP_YAML, MoreMediaTypes.TEXT_YAML})
  public void updateProjectSource(@PathParam("key") int datasetKey, @PathParam("id") int id, Dataset obj, @Auth User user) {
    if (obj==null) {
      throw new IllegalArgumentException("No source entity given for key " + id);
    }
    obj.setKey(id);
    obj.applyUser(user);
    int i = sourceDao.update(datasetKey, obj, user.getKey());
    if (i == 0) {
      throw NotFoundException.notFound(Dataset.class, DSID.of(datasetKey, id));
    }
  }

  @GET
  @Hidden
  @Path("/{key}/source/{id}/seo")
  @Produces({MediaType.TEXT_PLAIN, MediaType.TEXT_HTML})
  public Response getHtmlHeader(@PathParam("key") int datasetKey, @PathParam("id") int id) {
    var d = sourceDao.get(datasetKey, id, false);
    if (d == null) {
      throw NotFoundException.notFound(Dataset.class, DSID.of(datasetKey, id));
    }
    return ResourceUtils.streamFreemarker(d, "seo/dataset-seo.ftl", MediaType.TEXT_PLAIN_TYPE);
  }

  @GET
  @Path("/{key}/source/{id}/metrics")
  public ImportMetrics projectSourceMetrics(@PathParam("key") int datasetKey, @PathParam("id") int id) {
    return sourceDao.projectSourceMetrics(datasetKey, id);
  }
}
