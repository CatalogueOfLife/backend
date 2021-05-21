package life.catalogue.resources;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.assembly.AssemblyCoordinator;
import life.catalogue.assembly.AssemblyState;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.DatasetProjectSourceDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.SectorImportMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.UserMapper;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.MoreMediaTypes;
import life.catalogue.dw.jersey.filter.DatasetKeyRewriteFilter;
import life.catalogue.dw.jersey.filter.VaryAccept;
import life.catalogue.img.ImageService;
import life.catalogue.img.ImageServiceFS;
import life.catalogue.img.ImgConfig;
import life.catalogue.release.ReleaseManager;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

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
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetResource.class);
  private final DatasetDao dao;
  private final DatasetProjectSourceDao sourceDao;
  private final ImageService imgService;
  private final AssemblyCoordinator assembly;
  private final ReleaseManager releaseManager;

  public DatasetResource(SqlSessionFactory factory, DatasetDao dao, DatasetProjectSourceDao sourceDao, ImageService imgService, AssemblyCoordinator assembly, ReleaseManager releaseManager) {
    super(Dataset.class, dao, factory);
    this.dao = dao;
    this.sourceDao = sourceDao;
    this.imgService = imgService;
    this.assembly = assembly;
    this.releaseManager = releaseManager;
  }

  /**
   * @return the primary key of the object. Together with the CreatedResponseFilter will return a 201 location
   */
  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML, MoreMediaTypes.APP_YAML, MoreMediaTypes.TEXT_YAML})
  public Integer createAlt(@Valid Dataset obj, @Auth User user) {
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
  @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML, MoreMediaTypes.APP_YAML, MoreMediaTypes.TEXT_YAML})
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

  /**
   * Convenience method to get the latest release of a project.
   * This can also be achieved using the search, but it is a common operation we make as simple as possible in the API.
   *
   * See also {@link DatasetKeyRewriteFilter} on using <pre>LR</pre> as a suffix in dataset keys to indicate the latest release.
   * @param key
   */
  @GET
  @Path("{key}/latest")
  public Dataset getLatestRelease(@PathParam("key") int key) {
    return dao.latestRelease(key);
  }

  @GET
  @Path("{key}/assembly")
  public AssemblyState assemblyState(@PathParam("key") int key) {
    return assembly.getState(key);
  }
  
  @GET
  @Path("{key}/logo")
  @Produces("image/png")
  public BufferedImage logo(@PathParam("key") int key, @QueryParam("size") @DefaultValue("small") ImgConfig.Scale scale) {
    return imgService.datasetLogo(key, scale);
  }
  
  @POST
  @Path("{key}/logo")
  @Consumes({MediaType.APPLICATION_OCTET_STREAM,
      MoreMediaTypes.IMG_BMP, MoreMediaTypes.IMG_PNG, MoreMediaTypes.IMG_GIF,
      MoreMediaTypes.IMG_JPG, MoreMediaTypes.IMG_PSD, MoreMediaTypes.IMG_TIFF
  })
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Response uploadLogo(@PathParam("key") int key, InputStream img) throws IOException {
    imgService.putDatasetLogo(key, ImageServiceFS.read(img));
    return Response.ok().build();
  }
  
  @DELETE
  @Path("{key}/logo")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Response deleteLogo(@PathParam("key") int key) throws IOException {
    imgService.putDatasetLogo(key, null);
    return Response.ok().build();
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
    contrib.add(dm.get(datasetKey));
    sourceDao.list(datasetKey,null, false).forEach(contrib::add);

    return contrib;
  }

  @GET
  @Path("/{key}/source")
  public List<ArchivedDataset> projectSources(@PathParam("key") int datasetKey) {
    return sourceDao.list(datasetKey, null, false);
  }

  @GET
  @Path("/{key}/source/{id}")
  public ArchivedDataset projectSource(@PathParam("key") int datasetKey,
                                       @PathParam("id") int id,
                                       @QueryParam("original") boolean original,
                                       @Context SqlSession session) {
    return sourceDao.get(datasetKey, id, original);
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
  @Path("/{key}/source/{id}/logo")
  @Produces("image/png")
  public BufferedImage sourceLogo(@PathParam("key") int datasetKey, @PathParam("id") int id, @QueryParam("size") @DefaultValue("small") ImgConfig.Scale scale) {
    DatasetOrigin origin = DatasetInfoCache.CACHE.info(datasetKey).origin;
    if (!origin.isManagedOrRelease()) {
      throw new IllegalArgumentException("Dataset "+datasetKey+" is not a project");
    } else if (origin == DatasetOrigin.RELEASED) {
      return imgService.archiveDatasetLogo(id, datasetKey, scale);
    }
    return imgService.datasetLogo(id, scale);
  }

  @GET
  @Path("/{key}/source/{id}/metrics")
  public ImportMetrics projectSourceMetrics(@PathParam("key") int datasetKey, @PathParam("id") int id) {
    return sourceDao.projectSourceMetrics(datasetKey, id);
  }
}
