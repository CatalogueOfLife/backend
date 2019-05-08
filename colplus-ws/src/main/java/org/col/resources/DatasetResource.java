package org.col.resources;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.WsServerConfig;
import org.col.api.model.Dataset;
import org.col.api.model.DatasetImport;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.search.DatasetSearchRequest;
import org.col.api.vocab.ImportState;
import org.col.common.io.DownloadUtil;
import org.col.dao.DatasetDao;
import org.col.dao.DatasetImportDao;
import org.col.db.tree.DiffService;
import org.col.db.tree.NamesDiff;
import org.col.db.tree.TextTreePrinter;
import org.col.dw.auth.Roles;
import org.col.dw.jersey.MoreMediaTypes;
import org.col.img.ImageServiceFS;
import org.col.img.ImageService;
import org.col.img.ImgConfig;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset")
@SuppressWarnings("static-method")
public class DatasetResource extends GlobalEntityResource<Dataset> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetResource.class);
  private final DatasetDao dao;
  private final ImageService imgService;
  private final DatasetImportDao diDao;
  private final DiffService diff;
  private final SqlSessionFactory factory;
  
  public DatasetResource(SqlSessionFactory factory, ImageService imgService, WsServerConfig cfg, DownloadUtil downloader, DiffService diff) {
    super(Dataset.class, new DatasetDao(factory, downloader, imgService, cfg.normalizer::scratchFile));
    this.factory = factory;
    this.dao = (DatasetDao) super.dao;
    this.imgService = imgService;
    this.diDao = new DatasetImportDao(factory, cfg.textTreeRepo);
    this.diff = diff;
  }
  
  @GET
  public ResultPage<Dataset> search(@Valid @BeanParam Page page, @BeanParam DatasetSearchRequest req,
                                  @Context SqlSession session) {
    return dao.search(req, page);
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
  public Response textTree(@PathParam("key") int key,
                         @QueryParam("root") String rootID,
                         @QueryParam("rank") Set<Rank> ranks) {
    StreamingOutput stream = os -> {
      Writer writer = new BufferedWriter(new OutputStreamWriter(os));
      TextTreePrinter printer = TextTreePrinter.dataset(key, rootID, ranks, factory, writer);
      printer.print();
      writer.flush();
    };
    return Response.ok(stream).build();
  }

  @GET
  @Path("{key}/treediff")
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
    imgService.putDatasetLogo(get(key), ImageServiceFS.read(img));
    return Response.ok().build();
  }
  
  @DELETE
  @Path("{key}/logo")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Response deleteLogo(@PathParam("key") int key) throws IOException {
    imgService.putDatasetLogo(get(key), null);
    return Response.ok().build();
  }
  
}
