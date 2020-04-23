package life.catalogue.resources;

import io.dropwizard.auth.Auth;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.UserMapper;
import life.catalogue.db.tree.DiffService;
import life.catalogue.db.tree.NamesDiff;
import life.catalogue.db.tree.TextTreePrinter;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.MoreMediaTypes;
import life.catalogue.img.ImageService;
import life.catalogue.img.ImageServiceFS;
import life.catalogue.img.ImgConfig;
import life.catalogue.release.AcExporter;
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
import java.util.stream.Stream;

import static life.catalogue.api.model.User.userkey;

@Path("/dataset")
@SuppressWarnings("static-method")
public class DatasetResource extends AbstractGlobalResource<Dataset> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetResource.class);
  private final DatasetDao dao;
  private final ImageService imgService;
  private final DatasetImportDao diDao;
  private final DiffService diff;
  private final AcExporter exporter;

  public DatasetResource(SqlSessionFactory factory, DatasetDao dao, ImageService imgService, DatasetImportDao diDao, DiffService diff, AcExporter exporter) {
    super(Dataset.class, dao, factory);
    this.dao = dao;
    this.imgService = imgService;
    this.diDao = diDao;
    this.diff = diff;
    this.exporter = exporter;
  }
  
  @GET
  public ResultPage<Dataset> search(@Valid @BeanParam Page page, @BeanParam DatasetSearchRequest req, @Auth Optional<User> user) {
    return dao.search(req, userkey(user), page);
  }

  /**
   * Convenience method to get the latest release of a project.
   * This can also be achieved using the search, but it is a common operation we make as simple as possible in the API.
   *
   * See also {@link life.catalogue.dw.jersey.filter.DatasetKeyRequestFilter} on using <pre>LR</pre> as a suffix in dataset keys to indicate the latest release.
   * @param key
   */
  @GET
  @Path("{key}/latest")
  public Dataset getLatestRelease(@PathParam("key") int key) {
    return dao.latestRelease(key);
  }

  @POST
  @Path("{key}/export")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public boolean export(@PathParam("key") int key, @Auth User user) {
    return exportAC(key, user);
  }

  private boolean exportAC(int key, User user) {
    try {
      exporter.export(key);
      return true;

    } catch (Throwable e) {
      LOG.error("Error exporting dataset {}", key, e);
    }
    return false;
  }

  @GET
  @Path("{key}/import")
  public List<DatasetImport> getImports(@PathParam("key") int key,
                                        @QueryParam("state") List<ImportState> states,
                                        @QueryParam("limit") @DefaultValue("1") int limit) {
    return diDao.list(key, states, new Page(0, limit)).getResult();
  }
  
  @GET
  @Path("{key}/import/{attempt}")
  public DatasetImport getImportAttempt(@PathParam("key") int key,
                                        @PathParam("attempt") int attempt) {
    return diDao.getAttempt(key, attempt);
  }
  
  @GET
  @Path("{key}/import/{attempt}/tree")
  public Stream<String> getImportAttemptTree(@PathParam("key") int key,
                                     @PathParam("attempt") int attempt) throws IOException {
    return diDao.getTreeDao().getDatasetTree(key, attempt);
  }
  
  @GET
  @Path("{key}/import/{attempt}/names")
  public Stream<String> getImportAttemptNames(@PathParam("key") int key,
                                              @PathParam("attempt") int attempt) {
    return diDao.getTreeDao().getDatasetNames(key, attempt);
  }
  
  @GET
  @Path("{key}/texttree")
  @Produces(MediaType.TEXT_PLAIN)
  public Response textTree(@PathParam("key") int key,
                         @QueryParam("root") String rootID,
                         @QueryParam("rank") Set<Rank> ranks,
                         @Context SqlSession session) {
    StreamingOutput stream;
    Integer attempt = session.getMapper(DatasetMapper.class).lastImportAttempt(key);
    if (attempt != null && rootID == null && (ranks == null || ranks.isEmpty())) {
      // stream from pregenerated file
      stream = os -> {
        InputStream in = new FileInputStream(diDao.getTreeDao().treeFile(key, attempt));
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
  @Path("{key}/treediff")
  @Produces(MediaType.TEXT_PLAIN)
  public Reader diffTree(@PathParam("key") int key,
                         @QueryParam("attempts") String attempts,
                         @Context SqlSession session) throws IOException {
    return diff.datasetTreeDiff(key, attempts);
  }
  
  @GET
  @Path("{key}/namesdiff")
  public NamesDiff diffNames(@PathParam("key") int key,
                             @QueryParam("attempts") String attempts,
                             @Context SqlSession session) throws IOException {
    return diff.datasetNamesDiff(key, attempts);
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

  @GET
  @Path("{key}/editor")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public List<User> editors(@PathParam("key") int key, @Auth User user, @Context SqlSession session) {
    return session.getMapper(UserMapper.class).datasetEditors(key);
  }

  @POST
  @Path("/{key}/editor")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void addEditor(@PathParam("key") int key, int editorKey, @Auth User user, @Context SqlSession session) {
    if (!user.isAuthorized(key)) {
      throw new WebApplicationException(Response.Status.FORBIDDEN);
    }
    Dataset d = get(key);
    if (!d.getEditors().contains(editorKey)) {
      d.addEditor(editorKey);
      dao.update(d, user.getKey());
    }
  }

  @DELETE
  @Path("/{key}/editor/{editorKey}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void removeEditor(@PathParam("key") int key, @PathParam("editorKey") int editorKey, @Auth User user, @Context SqlSession session) {
    if (!user.isAuthorized(key)) {
      throw new WebApplicationException(Response.Status.FORBIDDEN);
    }
    Dataset d = get(key);
    if (d.getEditors().contains(editorKey)) {
      d.removeEditor(editorKey);
      dao.update(d, user.getKey());
    }
  }

}
