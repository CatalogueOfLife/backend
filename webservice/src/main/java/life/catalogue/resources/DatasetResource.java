package life.catalogue.resources;

import com.google.common.collect.Lists;
import io.dropwizard.auth.Auth;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.assembly.AssemblyCoordinator;
import life.catalogue.assembly.AssemblyState;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.NamesTreeDao;
import life.catalogue.db.mapper.*;
import life.catalogue.db.tree.TextTreePrinter;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.MoreMediaTypes;
import life.catalogue.dw.jersey.filter.DatasetKeyRewriteFilter;
import life.catalogue.img.ImageService;
import life.catalogue.img.ImageServiceFS;
import life.catalogue.img.ImgConfig;
import life.catalogue.release.ReleaseManager;
import org.apache.commons.io.IOUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static life.catalogue.api.model.User.userkey;

@Path("/dataset")
@SuppressWarnings("static-method")
public class DatasetResource extends AbstractGlobalResource<Dataset> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetResource.class);
  private final DatasetDao dao;
  private final ImageService imgService;
  private final DatasetImportDao diDao;
  private final AssemblyCoordinator assembly;
  private final ReleaseManager releaseManager;

  public DatasetResource(SqlSessionFactory factory, DatasetDao dao, ImageService imgService, DatasetImportDao diDao, AssemblyCoordinator assembly, ReleaseManager releaseManager) {
    super(Dataset.class, dao, factory);
    this.dao = dao;
    this.imgService = imgService;
    this.diDao = diDao;
    this.assembly = assembly;
    this.releaseManager = releaseManager;
  }

  @GET
  public ResultPage<Dataset> search(@Valid @BeanParam Page page, @BeanParam DatasetSearchRequest req, @Auth Optional<User> user) {
    return dao.search(req, userkey(user), page);
  }

  @GET
  @Path("{datasetKey}/settings")
  public DatasetSettings getSettings(@PathParam("datasetKey") int key) {
    return dao.getSettings(key);
  }

  @PUT
  @Path("{datasetKey}/settings")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void putSettings(@PathParam("datasetKey") int key, DatasetSettings settings, @Auth User user) {
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
  @Path("{datasetKey}/latest")
  public Dataset getLatestRelease(@PathParam("datasetKey") int key) {
    return dao.latestRelease(key);
  }

  @GET
  @Path("{datasetKey}/assembly")
  public AssemblyState assemblyState(@PathParam("datasetKey") int key) {
    return assembly.getState(key);
  }

  @GET
  @Path("{datasetKey}/texttree")
  @Produces(MediaType.TEXT_PLAIN)
  public Response textTree(@PathParam("datasetKey") int key,
                         @QueryParam("root") String rootID,
                         @QueryParam("rank") Set<Rank> ranks,
                         @Context SqlSession session) {
    StreamingOutput stream;
    Integer attempt = session.getMapper(DatasetMapper.class).lastImportAttempt(key);
    if (attempt != null && rootID == null && (ranks == null || ranks.isEmpty())) {
      // stream from pre-generated file
      stream = os -> {
        InputStream in = new FileInputStream(diDao.getTreeDao().treeFile(NamesTreeDao.Context.DATASET, key, attempt));
        IOUtils.copy(in, os);
        os.flush();
      };
  
    } else {
      stream = os -> {
        Writer writer = new BufferedWriter(new OutputStreamWriter(os));
        TextTreePrinter printer = TextTreePrinter.dataset(key, rootID, ranks, factory, writer);
        printer.print();
        if (printer.getCounter() == 0) {
          writer.write("--NONE--");
        }
        writer.flush();
      };
    }
    return Response.ok(stream).build();
  }
  
  @GET
  @Path("{datasetKey}/logo")
  @Produces("image/png")
  public BufferedImage logo(@PathParam("datasetKey") int key, @QueryParam("releaseKey") Integer releaseKey, @QueryParam("size") @DefaultValue("small") ImgConfig.Scale scale) {
    if (releaseKey != null) {
      return imgService.archiveDatasetLogo(releaseKey, key, scale);
    } else {
      return imgService.datasetLogo(key, scale);
    }
  }
  
  @POST
  @Path("{datasetKey}/logo")
  @Consumes({MediaType.APPLICATION_OCTET_STREAM,
      MoreMediaTypes.IMG_BMP, MoreMediaTypes.IMG_PNG, MoreMediaTypes.IMG_GIF,
      MoreMediaTypes.IMG_JPG, MoreMediaTypes.IMG_PSD, MoreMediaTypes.IMG_TIFF
  })
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Response uploadLogo(@PathParam("datasetKey") int key, InputStream img) throws IOException {
    imgService.putDatasetLogo(key, ImageServiceFS.read(img));
    return Response.ok().build();
  }
  
  @DELETE
  @Path("{datasetKey}/logo")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Response deleteLogo(@PathParam("datasetKey") int key) throws IOException {
    imgService.putDatasetLogo(key, null);
    return Response.ok().build();
  }

  @POST
  @Path("/{datasetKey}/copy")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Integer copy(@PathParam("datasetKey") int key, @Auth User user) {
    return releaseManager.duplicate(key, user);
  }

  @POST
  @Path("/{datasetKey}/release")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Integer release(@PathParam("datasetKey") int key, @Auth User user) {
    return releaseManager.release(key, user);
  }

  @GET
  @Path("{datasetKey}/editor")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public List<User> editors(@PathParam("datasetKey") int key, @Auth User user, @Context SqlSession session) {
    return session.getMapper(UserMapper.class).datasetEditors(key);
  }

  @POST
  @Path("/{datasetKey}/editor")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void addEditor(@PathParam("datasetKey") int key, int editorKey, @Auth User user) {
    dao.addEditor(key, editorKey, user);
  }

  @DELETE
  @Path("/{datasetKey}/editor/{editorKey}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void removeEditor(@PathParam("key") int key, @PathParam("editorKey") int editorKey, @Auth User user) {
    dao.removeEditor(key, editorKey, user);
  }

  @GET
  @Path("/{datasetKey}/source")
  public List<ProjectSourceDataset> projectSources(@PathParam("datasetKey") int datasetKey, @Context SqlSession session) {
    return Lists.newArrayList(session.getMapper(ProjectSourceMapper.class).processDataset(datasetKey));
  }

  @GET
  @Path("/{datasetKey}/source/{key}")
  public ProjectSourceDataset projectSource(@PathParam("datasetKey") int datasetKey, @PathParam("key") int key, @Context SqlSession session) {
    return session.getMapper(ProjectSourceMapper.class).get(key, datasetKey);
  }

  @GET
  @Path("/{datasetKey}/source/{key}/metrics")
  public ImportMetrics projectSourceMetrics(@PathParam("datasetKey") int datasetKey, @PathParam("key") int key, @Context SqlSession session) {
    ImportMetrics metrics = new ImportMetrics();
    metrics.setAttempt(-1);
    metrics.setDatasetKey(datasetKey);
    for (ImportMetrics m : session.getMapper(SectorImportMapper.class).list(null, datasetKey, key, null, true, null)) {
      metrics.add(m);
    }
    return metrics;
  }
}
