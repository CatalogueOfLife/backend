package org.col.resources;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.Dataset;
import org.col.api.model.DatasetImport;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.search.DatasetSearchRequest;
import org.col.api.vocab.ImportState;
import org.col.db.dao.DatasetDao;
import org.col.db.dao.DatasetImportDao;
import org.col.db.mapper.DatasetMapper;
import org.col.dw.auth.Roles;
import org.col.dw.jersey.MoreMediaTypes;
import org.col.img.ImageService;
import org.col.img.ImgConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset")
@SuppressWarnings("static-method")
public class DatasetResource extends CRUDIntResource<Dataset> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetResource.class);
  private final SqlSessionFactory factory;
  private final ImageService imgService;
  
  public DatasetResource(SqlSessionFactory factory, ImageService imgService) {
    super(Dataset.class, DatasetMapper.class);
    this.factory = factory;
    this.imgService = imgService;
  }
  
  @GET
  public ResultPage<Dataset> list(@Valid @BeanParam Page page, @BeanParam DatasetSearchRequest req,
                                  @Context SqlSession session) {
    return new DatasetDao(session).search(req, page);
  }
  
  @GET
  @Path("{key}/import")
  public List<DatasetImport> getImports(@PathParam("key") int key,
                                        @QueryParam("state") List<ImportState> states,
                                        @QueryParam("limit") @DefaultValue("1") int limit) {
    return new DatasetImportDao(factory).list(key, states, new Page(0, limit)).getResult();
  }
  
  @GET
  @Path("{key}/logo")
  @Produces("image/png")
  public BufferedImage logo(@PathParam("key") int key, @QueryParam("size") @DefaultValue("small") ImgConfig.Scale scale) {
    //return Response.ok(imgService.datasetLogo(key, scale)).build();
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
    imgService.putDatasetLogo(get(key), ImageService.read(img));
    return Response.ok().build();
  }
  
  @DELETE
  @Path("{key}/logo")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Response deleteLogo(@PathParam("key") int key) throws IOException {
    imgService.putDatasetLogo(get(key), null);
    return Response.ok().build();
  }
  
  private Dataset get(int key) {
    try (SqlSession sess = factory.openSession()) {
      return sess.getMapper(DatasetMapper.class).get(key);
    }
  }
}
