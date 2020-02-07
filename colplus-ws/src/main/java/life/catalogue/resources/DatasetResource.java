package life.catalogue.resources;

import io.dropwizard.auth.Auth;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.tree.DiffService;
import life.catalogue.db.tree.NamesDiff;
import life.catalogue.db.tree.TextTreePrinter;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.MoreMediaTypes;
import life.catalogue.es.name.index.NameUsageIndexService;
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
import java.util.Set;
import java.util.stream.Stream;

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

  public DatasetResource(SqlSessionFactory factory, ImageService imgService, DatasetImportDao diDao, WsServerConfig cfg,
                         DownloadUtil downloader, DiffService diff, NameUsageIndexService indexService, AcExporter exporter) {
    super(Dataset.class,
            new DatasetDao(factory, downloader, imgService, diDao, indexService, cfg.normalizer::scratchFile),
            factory
    );
    this.dao = (DatasetDao) super.dao;
    this.imgService = imgService;
    this.diDao = diDao;
    this.diff = diff;
    this.exporter = exporter;
  }
  
  @GET
  public ResultPage<Dataset> search(@Valid @BeanParam Page page, @BeanParam DatasetSearchRequest req) {
    return dao.search(req, page);
  }
  
  @GET
  @Path("catalogues")
  public List<Integer> listCatalogues(@Context SqlSession session) {
    return session.getMapper(SectorMapper.class).listTargetDatasetKeys();
  }

  @POST
  @Path("{key}/export")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public String export(@PathParam("key") int key, @Auth ColUser user) {
    life.catalogue.release.Logger logger = new life.catalogue.release.Logger(LOG);
    try {
      exporter.export(key, logger);
    } catch (Throwable e) {
      LOG.error("Error exporting dataset {}", key, e);
      logger.log("\n\nERROR!");
      if (e.getMessage() != null) {
        logger.log(e.getMessage());
      }
    }
    return logger.toString();
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
    Integer attempt = session.getMapper(DatasetMapper.class).lastImportAttempt(key);
    if (attempt == null) {
      throw new NotFoundException();
    }
    
    StreamingOutput stream;
    if (rootID == null && (ranks == null || ranks.isEmpty())) {
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
  
}
